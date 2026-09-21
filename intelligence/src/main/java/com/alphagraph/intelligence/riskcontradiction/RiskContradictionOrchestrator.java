package com.alphagraph.intelligence.riskcontradiction;

import com.alphagraph.reference.api.TrackedInstrumentSummary;
import com.alphagraph.reference.instrument.InstrumentReader;
import com.alphagraph.risk.contradiction.RiskContradictionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as every other family's Stage 2
 * orchestrator. Drives off {@code reference.instrument.InstrumentReader.listAll()} - the full real
 * tracked universe, not any single family's own {@code DISTINCT instrument_id} - since this family
 * reads across 4 different families and no one of their universes is authoritative for a
 * cross-domain meta-engine (same reasoning {@code intelligence.risk.RiskAnalysisOrchestrator}
 * already applies for its own multi-domain reads). No {@code Clock} dependency - every
 * {@code asOfDate} is derived from the evidence itself (see {@code RiskContradictionEngine}'s
 * javadoc).
 */
@Component
class RiskContradictionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RiskContradictionOrchestrator.class);

    private final InstrumentReader instrumentReader;
    private final OwnershipContradictionReader ownershipReader;
    private final FinancialGrowthReader financialReader;
    private final MarketDeliveryReader marketReader;
    private final CapitalAllocationEquityRaiseReader corporateReader;
    private final RiskContradictionEngine engine;
    private final RiskContradictionWriter writer;

    RiskContradictionOrchestrator(
        InstrumentReader instrumentReader, OwnershipContradictionReader ownershipReader, FinancialGrowthReader financialReader,
        MarketDeliveryReader marketReader, CapitalAllocationEquityRaiseReader corporateReader,
        RiskContradictionEngine engine, RiskContradictionWriter writer
    ) {
        this.instrumentReader = instrumentReader;
        this.ownershipReader = ownershipReader;
        this.financialReader = financialReader;
        this.marketReader = marketReader;
        this.corporateReader = corporateReader;
        this.engine = engine;
        this.writer = writer;
    }

    void run() {
        List<TrackedInstrumentSummary> instruments = instrumentReader.listAll();

        int succeeded = 0;
        int failed = 0;
        for (TrackedInstrumentSummary instrument : instruments) {
            try {
                var ownershipSignal = ownershipReader.findLatestState(instrument.id()).orElse(null);
                var financialState = financialReader.findLatestState(instrument.id()).orElse(null);
                var marginPoint = financialReader.findLatestOperatingMarginChange(instrument.id()).orElse(null);
                var marketSignal = marketReader.findLatestState(instrument.id()).orElse(null);
                var pricePoint = marketReader.findLatestPriceReturnChange(instrument.id()).orElse(null);
                var corporateSignal = corporateReader.findLatestState(instrument.id()).orElse(null);
                if (ownershipSignal == null && financialState == null && marginPoint == null
                    && marketSignal == null && pricePoint == null && corporateSignal == null) {
                    continue;
                }

                // Only attempted when the equity-raise prerequisite already holds - it needs that
                // anchor date, and there is nothing to anchor to otherwise.
                var financialAsOfAnchor = (corporateSignal != null && corporateSignal.hasReason("EQUITY_RAISE_EVENT_COUNT_180D_NONZERO"))
                    ? financialReader.findStateAsOf(instrument.id(), corporateSignal.asOfDate()).orElse(null)
                    : null;

                RiskContradictionResult result = engine.calculate(
                    instrument.id(), instrument.symbol(), ownershipSignal, financialState, marginPoint,
                    marketSignal, pricePoint, corporateSignal, financialAsOfAnchor
                );
                writer.write(
                    result.instrumentId(), result.symbol(), result.asOfDate(), result.primaryState().name(),
                    result.evidenceCoveragePct(), result.dataReadiness().name(), result.confidence(),
                    result.reasons().stream()
                        .map(r -> new RiskContradictionWriter.ReasonEntry(r.code(), r.metricValue(), r.evidenceReference()))
                        .toList()
                );
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to compute risk contradiction state for instrument {}: {}", instrument.id(), e.getMessage());
            }
        }

        log.info("Risk contradiction run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }
}
