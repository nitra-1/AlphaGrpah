package com.alphagraph.financial.transformation;

import com.alphagraph.common.rules.RuleSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as every Stage 2/3 orchestrator.
 * Drives off {@code FinancialTransformationEvidenceReader.findAllInstrumentIds()} - Stage 1's own
 * real universe, same choice every prior Stage 3 domain made.
 *
 * <p><b>{@link #backfill()} exists but is not exposed as a runnable job</b> (no scheduler trigger
 * method, no {@code JobRegistry}/{@code CronMonitoringRepository} wiring) - Financial's
 * {@code as_of_date} is the quarter-<i>end</i> date, not the date results were actually filed and
 * known to the market (no {@code available_from}/{@code result_publication_date} field exists
 * upstream today), so a historical replay built on it would silently introduce real look-ahead
 * bias. This method stays correct and covered by tests (its own logic never assumes anything about
 * whether it's exposed), ready to be wired up once a real publication-date field exists for
 * Financial evidence - that reactivation is a deliberate, visible follow-up change, not a flag to
 * flip. {@link #run()} (forward-only, using whatever is currently the latest real data) never
 * claims to reconstruct a past point in time and is unaffected by this limitation.
 */
@Component
class FinancialTransformationSequenceOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(FinancialTransformationSequenceOrchestrator.class);
    /** Comfortably covers the 6-period default max sequence age plus buffer for a deep-history instrument. */
    private static final int HISTORY_LIMIT = 30;

    private final FinancialTransformationEvidenceReader evidenceReader;
    private final FinancialInflectionHistoryReader historyReader;
    private final FinancialSequenceRuleSetLoader ruleSetLoader;
    private final FinancialTransformationSequenceEngine engine;
    private final FinancialTransformationSequenceWriter sequenceWriter;
    private final FinancialSequenceReadinessWriter readinessWriter;

    FinancialTransformationSequenceOrchestrator(
        FinancialTransformationEvidenceReader evidenceReader, FinancialInflectionHistoryReader historyReader,
        FinancialSequenceRuleSetLoader ruleSetLoader, FinancialTransformationSequenceEngine engine,
        FinancialTransformationSequenceWriter sequenceWriter, FinancialSequenceReadinessWriter readinessWriter
    ) {
        this.evidenceReader = evidenceReader;
        this.historyReader = historyReader;
        this.ruleSetLoader = ruleSetLoader;
        this.engine = engine;
        this.sequenceWriter = sequenceWriter;
        this.readinessWriter = readinessWriter;
    }

    void run() {
        RuleSet rules = ruleSetLoader.loadActiveRules();
        List<UUID> instrumentIds = evidenceReader.findAllInstrumentIds();

        int succeeded = 0;
        int failed = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                evaluateAndWrite(instrumentId, null, rules);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to compute financial transformation sequences for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Financial transformation sequence run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }

    /** Not point-in-time safe for Financial - see this class's own javadoc. Not wired to any runnable job. */
    void backfill() {
        RuleSet rules = ruleSetLoader.loadActiveRules();
        List<UUID> instrumentIds = evidenceReader.findAllInstrumentIds();

        int succeeded = 0;
        int failed = 0;
        int pointsWritten = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                List<LocalDate> realDates = historyReader.findAllAsOfDates(instrumentId);
                for (LocalDate asOfDate : realDates) {
                    evaluateAndWrite(instrumentId, asOfDate, rules);
                    pointsWritten++;
                }
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to backfill financial transformation sequences for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Financial transformation sequence backfill complete: {} instruments succeeded, {} failed, {} quarters replayed", succeeded, failed, pointsWritten);
    }

    private void evaluateAndWrite(UUID instrumentId, LocalDate upToInclusiveOrNull, RuleSet rules) {
        List<FinancialInflectionHistoryEntry> history = historyReader.findHistory(instrumentId, upToInclusiveOrNull, HISTORY_LIMIT);
        if (history.isEmpty()) {
            return;
        }
        FinancialInflectionHistoryEntry latest = history.get(history.size() - 1);
        String symbol = latest.symbol();
        LocalDate asOfDate = latest.asOfDate();

        readinessWriter.write(engine.evaluateReadiness(instrumentId, symbol, asOfDate, history));

        engine.evaluateBusinessAccelerationCycle(instrumentId, symbol, history, rules).ifPresent(sequenceWriter::write);
        engine.evaluateOperatingLeverageCycle(instrumentId, symbol, history, rules).ifPresent(sequenceWriter::write);
        engine.evaluateMultiQuarterEarningsExpansion(instrumentId, symbol, history, rules).ifPresent(sequenceWriter::write);
        engine.evaluateInterestCostReliefTrend(instrumentId, symbol, history, rules).ifPresent(sequenceWriter::write);
    }
}
