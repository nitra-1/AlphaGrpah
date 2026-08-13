package com.alphagraph.learning.outcomes;

import com.alphagraph.intelligence.priceadjustment.AdjustedDailyPrice;
import com.alphagraph.intelligence.priceadjustment.PriceAdjustmentService;
import com.alphagraph.reference.instrument.InstrumentReader;
import com.alphagraph.reference.instrument.SectorBenchmarkReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Market- and sector-relative forward return, each independently nullable and computed the same
 * way: fetch the benchmark instrument's own adjusted price history (same
 * {@link PriceAdjustmentService#adjustedHistory} every subject instrument uses), require an exact
 * trading-date match on both the reference date and the outcome date - never substituted from the
 * nearest available date, since that would silently change what "20D" means without disclosing it
 * - and compute {@code subjectForwardReturn - benchmarkReturn}.
 *
 * <p>Market benchmark resolves a configured symbol (default {@code NIFTY50}) via
 * {@link InstrumentReader#findIdBySymbol}; unavailable (empty result) until that instrument is
 * actually tracked with real price history. Sector benchmark resolves via
 * {@link SectorBenchmarkReader}, which only has a row for sectors with a verified real NSE index -
 * most sectors in this platform's taxonomy don't, and correctly get {@code UNAVAILABLE} rather than
 * a guessed mapping.
 */
@Component
class BenchmarkReturnCalculator {

    private static final int RETURN_SCALE = 2;

    private final InstrumentReader instrumentReader;
    private final SectorBenchmarkReader sectorBenchmarkReader;
    private final PriceAdjustmentService priceAdjustmentService;
    private final String marketBenchmarkSymbol;

    BenchmarkReturnCalculator(
        InstrumentReader instrumentReader, SectorBenchmarkReader sectorBenchmarkReader,
        PriceAdjustmentService priceAdjustmentService,
        @Value("${alphagraph.learning.market-benchmark-symbol:NIFTY50}") String marketBenchmarkSymbol
    ) {
        this.instrumentReader = instrumentReader;
        this.sectorBenchmarkReader = sectorBenchmarkReader;
        this.priceAdjustmentService = priceAdjustmentService;
        this.marketBenchmarkSymbol = marketBenchmarkSymbol;
    }

    BenchmarkResult computeMarket(LocalDate asOfDate, LocalDate outcomeDate, BigDecimal subjectForwardReturn) {
        return instrumentReader.findIdBySymbol(marketBenchmarkSymbol)
            .map(benchmarkId -> computeFor(benchmarkId, asOfDate, outcomeDate, subjectForwardReturn))
            .orElse(BenchmarkResult.UNAVAILABLE);
    }

    BenchmarkResult computeSector(UUID instrumentId, LocalDate asOfDate, LocalDate outcomeDate, BigDecimal subjectForwardReturn) {
        return sectorBenchmarkReader.findBenchmarkInstrumentIdForInstrument(instrumentId)
            .map(benchmarkId -> computeFor(benchmarkId, asOfDate, outcomeDate, subjectForwardReturn))
            .orElse(BenchmarkResult.UNAVAILABLE);
    }

    private BenchmarkResult computeFor(UUID benchmarkInstrumentId, LocalDate asOfDate, LocalDate outcomeDate, BigDecimal subjectForwardReturn) {
        List<AdjustedDailyPrice> history = priceAdjustmentService.adjustedHistory(benchmarkInstrumentId);
        Optional<AdjustedDailyPrice> referenceDay = findExactDate(history, asOfDate);
        Optional<AdjustedDailyPrice> outcomeDay = findExactDate(history, outcomeDate);
        if (referenceDay.isEmpty() || outcomeDay.isEmpty()) {
            return BenchmarkResult.UNAVAILABLE;
        }

        BigDecimal benchmarkReturn = outcomeDay.get().adjustedClose().subtract(referenceDay.get().adjustedClose())
            .divide(referenceDay.get().adjustedClose(), 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(RETURN_SCALE, RoundingMode.HALF_UP);
        BigDecimal excessReturn = subjectForwardReturn.subtract(benchmarkReturn).setScale(RETURN_SCALE, RoundingMode.HALF_UP);

        return new BenchmarkResult(benchmarkInstrumentId, benchmarkReturn, outcomeDay.get().tradeDate(), excessReturn);
    }

    private static Optional<AdjustedDailyPrice> findExactDate(List<AdjustedDailyPrice> history, LocalDate date) {
        return history.stream().filter(price -> price.tradeDate().equals(date)).findFirst();
    }

    record BenchmarkResult(
        UUID benchmarkInstrumentId, BigDecimal returnPercentage, LocalDate outcomeDate, BigDecimal excessReturnPercentage
    ) {
        static final BenchmarkResult UNAVAILABLE = new BenchmarkResult(null, null, null, null);
    }
}
