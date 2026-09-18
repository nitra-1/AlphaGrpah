package com.alphagraph.intelligence.sectorcontext;

import com.alphagraph.reference.api.TrackedInstrumentSummary;
import com.alphagraph.reference.instrument.InstrumentReader;
import com.alphagraph.reference.instrument.SectorBenchmarkReader;
import com.alphagraph.sector.api.SectorScore;
import com.alphagraph.sector.engine.SectorScoreReader;
import com.alphagraph.sector.transformation.SectorContextEvidenceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as
 * {@code ownership.transformation.OwnershipTransformationOrchestrator}. Drives off
 * {@code reference.instrument.InstrumentReader.listAll()} (id/symbol/sector in one call, no
 * separate symbol resolution needed) - a read-only call to an existing public method.
 * NIFTY50's own return series is fetched once outside the per-instrument loop and reused for
 * every instrument's VS_NIFTY spread; each sector's benchmark series is fetched at most once per
 * run and cached, since multiple instruments in the same sector share the same benchmark.
 */
@Component
class SectorContextOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SectorContextOrchestrator.class);
    private static final int SERIES_LIMIT = 25;
    /** Comfortably covers every real trading day in market.daily_prices (111 as of this writing) for a one-off backfill. */
    private static final int BACKFILL_SERIES_LIMIT = 500;

    private final InstrumentReader instrumentReader;
    private final MarketPriceReturnLookup priceReturnLookup;
    private final SectorBenchmarkReader sectorBenchmarkReader;
    private final SectorScoreReader sectorScoreReader;
    private final SectorContextEngine engine;
    private final SectorContextEvidenceWriter evidenceWriter;
    private final String niftySymbol;

    SectorContextOrchestrator(
        InstrumentReader instrumentReader, MarketPriceReturnLookup priceReturnLookup, SectorBenchmarkReader sectorBenchmarkReader,
        SectorScoreReader sectorScoreReader, SectorContextEngine engine, SectorContextEvidenceWriter evidenceWriter,
        @Value("${alphagraph.intelligence.sector-context.market-benchmark-symbol:NIFTY50}") String niftySymbol
    ) {
        this.instrumentReader = instrumentReader;
        this.priceReturnLookup = priceReturnLookup;
        this.sectorBenchmarkReader = sectorBenchmarkReader;
        this.sectorScoreReader = sectorScoreReader;
        this.engine = engine;
        this.evidenceWriter = evidenceWriter;
        this.niftySymbol = niftySymbol;
    }

    void run() {
        List<TrackedInstrumentSummary> instruments = instrumentReader.listAll();
        Optional<UUID> niftyId = instrumentReader.findIdBySymbol(niftySymbol);
        List<PriceReturnPoint> niftySeries = niftyId.map(id -> priceReturnLookup.findRecentAscending(id, SERIES_LIMIT)).orElse(List.of());
        Map<UUID, List<PriceReturnPoint>> benchmarkSeriesCache = new HashMap<>();

        int succeeded = 0;
        int failed = 0;
        for (TrackedInstrumentSummary instrument : instruments) {
            try {
                for (InstrumentSeries series : buildSeries(instrument, niftyId, niftySeries, benchmarkSeriesCache, SERIES_LIMIT)) {
                    if (!series.values().isEmpty()) {
                        engine.calculate(series.metric(), instrument.id(), instrument.symbol(), series.values()).ifPresent(this::write);
                    }
                }
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to compute sector context evidence for instrument {}: {}", instrument.id(), e.getMessage());
            }
        }

        log.info("Sector context evidence run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }

    /**
     * One-off historical catch-up, not a daily concern - fetches a much longer real series per
     * instrument (comfortably covering every real trading day, not just the last 25) and replays
     * {@link SectorContextEngine#calculate} unchanged against every valid prefix of it, instead of
     * only the most recent point. Must be triggered after {@code market-accumulation-evidence-backfill}
     * has completed - {@code VS_NIFTY}/{@code VS_SECTOR} read {@code market.transformation_evidence}
     * directly, so there is nothing to replay against until that table has real historical depth.
     */
    void backfill() {
        List<TrackedInstrumentSummary> instruments = instrumentReader.listAll();
        Optional<UUID> niftyId = instrumentReader.findIdBySymbol(niftySymbol);
        List<PriceReturnPoint> niftySeries = niftyId.map(id -> priceReturnLookup.findRecentAscending(id, BACKFILL_SERIES_LIMIT)).orElse(List.of());
        Map<UUID, List<PriceReturnPoint>> benchmarkSeriesCache = new HashMap<>();

        int succeeded = 0;
        int failed = 0;
        int pointsWritten = 0;
        for (TrackedInstrumentSummary instrument : instruments) {
            try {
                for (InstrumentSeries series : buildSeries(instrument, niftyId, niftySeries, benchmarkSeriesCache, BACKFILL_SERIES_LIMIT)) {
                    List<DatedValue> values = series.values();
                    for (int i = 1; i < values.size(); i++) {
                        engine.calculate(series.metric(), instrument.id(), instrument.symbol(), values.subList(0, i + 1)).ifPresent(this::write);
                        pointsWritten++;
                    }
                }
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to backfill sector context evidence for instrument {}: {}", instrument.id(), e.getMessage());
            }
        }

        log.info("Sector context evidence backfill complete: {} instruments succeeded, {} failed, {} evidence points replayed", succeeded, failed, pointsWritten);
    }

    /** The three metrics' aligned series for one instrument, built the same way regardless of whether the caller wants just the latest point or every point to replay. */
    private List<InstrumentSeries> buildSeries(
        TrackedInstrumentSummary instrument, Optional<UUID> niftyId, List<PriceReturnPoint> niftySeries,
        Map<UUID, List<PriceReturnPoint>> benchmarkSeriesCache, int limit
    ) {
        List<InstrumentSeries> series = new ArrayList<>();

        List<PriceReturnPoint> instrumentSeries = priceReturnLookup.findRecentAscending(instrument.id(), limit);
        if (instrumentSeries.isEmpty()) {
            return series;
        }

        if (!niftySeries.isEmpty() && !instrument.id().equals(niftyId.orElse(null))) {
            series.add(new InstrumentSeries(SectorContextMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY, spreadSeries(instrumentSeries, niftySeries)));
        }

        Optional<UUID> sectorBenchmarkId = sectorBenchmarkReader.findBenchmarkInstrumentIdForInstrument(instrument.id());
        if (sectorBenchmarkId.isPresent()) {
            List<PriceReturnPoint> benchmarkSeries = benchmarkSeriesCache.computeIfAbsent(
                sectorBenchmarkId.get(), id -> priceReturnLookup.findRecentAscending(id, limit)
            );
            if (!benchmarkSeries.isEmpty()) {
                series.add(new InstrumentSeries(SectorContextMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR, spreadSeries(instrumentSeries, benchmarkSeries)));
            }
        }

        List<SectorScore> recentScores = sectorScoreReader.findRecentForInstrument(instrument.id(), limit);
        series.add(new InstrumentSeries(SectorContextMetric.SECTOR_RELATIVE_STRENGTH, relativeStrengthSeries(recentScores)));

        return series;
    }

    private record InstrumentSeries(SectorContextMetric metric, List<DatedValue> values) {
    }

    private void write(SectorContextEvidenceObservation observation) {
        evidenceWriter.write(
            observation.instrumentId(), observation.symbol(), observation.metric().name(), observation.asOfDate(), observation.priorAsOfDate(),
            observation.value(), observation.priorValue(), observation.change(), observation.persistenceDays(), observation.confidence()
        );
    }

    /** Ascending inner join by date - only dates present in both series, never substituted from the nearest available date. */
    private static List<DatedValue> spreadSeries(List<PriceReturnPoint> instrumentAscending, List<PriceReturnPoint> benchmarkAscending) {
        Map<LocalDate, BigDecimal> benchmarkByDate = new HashMap<>();
        for (PriceReturnPoint point : benchmarkAscending) {
            benchmarkByDate.put(point.tradeDate(), point.value());
        }
        List<DatedValue> spread = new ArrayList<>();
        for (PriceReturnPoint point : instrumentAscending) {
            BigDecimal benchmarkValue = benchmarkByDate.get(point.tradeDate());
            if (benchmarkValue != null) {
                spread.add(new DatedValue(point.tradeDate(), point.value().subtract(benchmarkValue)));
            }
        }
        return spread;
    }

    private static List<DatedValue> relativeStrengthSeries(List<SectorScore> recentDescending) {
        List<DatedValue> ascending = new ArrayList<>();
        for (int i = recentDescending.size() - 1; i >= 0; i--) {
            SectorScore score = recentDescending.get(i);
            if (score.relativeStrength() != null) {
                ascending.add(new DatedValue(score.asOfDate(), BigDecimal.valueOf(score.relativeStrength())));
            }
        }
        return ascending;
    }
}
