package com.alphagraph.financial.transformation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as every other family's Stage 2
 * orchestrator. No {@code Clock} dependency, unlike Market's - {@code asOfDate} is derived entirely
 * from the evidence itself (see {@code FinancialInflectionResult}'s javadoc for why quarterly-cadence
 * data must never use "today").
 */
@Component
class FinancialInflectionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(FinancialInflectionOrchestrator.class);
    private static final int ACCELERATION_SERIES_LIMIT = 4;

    private final FinancialTransformationEvidenceReader evidenceReader;
    private final FinancialInflectionEngine engine;
    private final FinancialInflectionWriter writer;

    FinancialInflectionOrchestrator(FinancialTransformationEvidenceReader evidenceReader, FinancialInflectionEngine engine, FinancialInflectionWriter writer) {
        this.evidenceReader = evidenceReader;
        this.engine = engine;
        this.writer = writer;
    }

    void run() {
        List<UUID> instrumentIds = evidenceReader.findAllInstrumentIds();

        int succeeded = 0;
        int failed = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                var revenueRows = evidenceReader.findRecent(instrumentId, FinancialMetric.REVENUE, ACCELERATION_SERIES_LIMIT);
                var patRows = evidenceReader.findRecent(instrumentId, FinancialMetric.PAT, ACCELERATION_SERIES_LIMIT);
                var latestMargin = evidenceReader.findLatest(instrumentId, FinancialMetric.OPERATING_MARGIN).orElse(null);
                var latestInterestExpense = evidenceReader.findLatest(instrumentId, FinancialMetric.INTEREST_EXPENSE).orElse(null);
                if (revenueRows.isEmpty() && patRows.isEmpty() && latestMargin == null && latestInterestExpense == null) {
                    continue;
                }
                String symbol = firstSymbol(revenueRows, patRows, latestMargin, latestInterestExpense);

                FinancialInflectionResult result = engine.calculate(instrumentId, symbol, revenueRows, patRows, latestMargin, latestInterestExpense);
                writer.write(result);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to compute financial inflection state for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Financial inflection run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }

    private static String firstSymbol(
        List<FinancialEvidenceObservation> revenueRows, List<FinancialEvidenceObservation> patRows,
        FinancialEvidenceObservation latestMargin, FinancialEvidenceObservation latestInterestExpense
    ) {
        if (!revenueRows.isEmpty()) {
            return revenueRows.get(0).symbol();
        }
        if (!patRows.isEmpty()) {
            return patRows.get(0).symbol();
        }
        if (latestMargin != null) {
            return latestMargin.symbol();
        }
        if (latestInterestExpense != null) {
            return latestInterestExpense.symbol();
        }
        throw new IllegalStateException("No observation carried a symbol");
    }
}
