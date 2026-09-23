package com.alphagraph.corporate.transformation;

import com.alphagraph.common.rules.RuleSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as every Stage 2/3 orchestrator.
 * Drives off {@code CapitalAllocationEvidenceReader.findAllInstrumentIds()} - Stage 1's own real
 * universe, same choice every prior Stage 3 domain made. Reads both metrics' Stage 1 evidence
 * directly, in parallel, never {@code corporate.inflection_states} - see
 * {@code CapitalAllocationTransformationSequenceEngine}'s javadoc for why.
 *
 * <p><b>Unlike Financial, {@link #backfill()} is wired to a real, runnable job</b> -
 * {@code corporate.transformation_evidence.as_of_date} is a real, {@code Clock}-based evidence
 * date throughout (never a period-end proxy), and the evidence itself is append-only and never
 * rewritten, so a historical replay is point-in-time safe today. One disclosed, forward-looking
 * caveat carried from the engine's own javadoc: if Stage 1 evidence were ever itself recomputed
 * from a *current* snapshot of {@code corporate.corporate_actions} (it is not today), that would
 * retroactively leak late-discovered actions into old historical rows, and this backfill would
 * silently inherit that look-ahead bias - not applicable to the code as it exists now.
 */
@Component
class CapitalAllocationTransformationSequenceOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CapitalAllocationTransformationSequenceOrchestrator.class);
    /** Comfortably covers the 540-day default max sequence age plus buffer - evidence is dense daily once an instrument's first qualifying action lands. */
    private static final int HISTORY_LIMIT = 600;
    /** Wide enough to enumerate every real historical as_of_date for backfill, not just the recent window evaluateAndWrite bounds itself to. */
    private static final int BACKFILL_DATE_LIMIT = 5000;

    private final CapitalAllocationEvidenceReader evidenceReader;
    private final CapitalAllocationSequenceRuleSetLoader ruleSetLoader;
    private final CapitalAllocationTransformationSequenceEngine engine;
    private final CapitalAllocationTransformationSequenceWriter sequenceWriter;
    private final CapitalAllocationSequenceReadinessWriter readinessWriter;

    CapitalAllocationTransformationSequenceOrchestrator(
        CapitalAllocationEvidenceReader evidenceReader, CapitalAllocationSequenceRuleSetLoader ruleSetLoader,
        CapitalAllocationTransformationSequenceEngine engine, CapitalAllocationTransformationSequenceWriter sequenceWriter,
        CapitalAllocationSequenceReadinessWriter readinessWriter
    ) {
        this.evidenceReader = evidenceReader;
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
                log.warn("Failed to compute capital allocation transformation sequences for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Capital allocation transformation sequence run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }

    /**
     * One-off historical catch-up, not a daily concern - replays {@code evaluateAndWrite} against
     * every real historical {@code as_of_date} an instrument has (the union of both metrics' own
     * dates), each time bounded to that exact date (docs/008 §16's point-in-time rule).
     */
    void backfill() {
        RuleSet rules = ruleSetLoader.loadActiveRules();
        List<UUID> instrumentIds = evidenceReader.findAllInstrumentIds();

        int succeeded = 0;
        int failed = 0;
        int pointsWritten = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                for (LocalDate asOfDate : findAllAsOfDates(instrumentId)) {
                    evaluateAndWrite(instrumentId, asOfDate, rules);
                    pointsWritten++;
                }
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to backfill capital allocation transformation sequences for instrument {}: {}", instrumentId, e.getMessage());
            }
        }

        log.info("Capital allocation transformation sequence backfill complete: {} instruments succeeded, {} failed, {} days replayed", succeeded, failed, pointsWritten);
    }

    private void evaluateAndWrite(UUID instrumentId, LocalDate upToInclusiveOrNull, RuleSet rules) {
        List<CapitalAllocationEvidenceObservation> buybackHistory =
            evidenceReader.findHistory(instrumentId, CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D, upToInclusiveOrNull, HISTORY_LIMIT);
        List<CapitalAllocationEvidenceObservation> equityRaiseHistory =
            evidenceReader.findHistory(instrumentId, CapitalAllocationMetric.EQUITY_RAISE_EVENT_COUNT_180D, upToInclusiveOrNull, HISTORY_LIMIT);
        if (buybackHistory.isEmpty() && equityRaiseHistory.isEmpty()) {
            return;
        }
        String symbol = !buybackHistory.isEmpty() ? buybackHistory.get(buybackHistory.size() - 1).symbol() : equityRaiseHistory.get(equityRaiseHistory.size() - 1).symbol();
        LocalDate latestBuyback = buybackHistory.isEmpty() ? null : buybackHistory.get(buybackHistory.size() - 1).asOfDate();
        LocalDate latestEquityRaise = equityRaiseHistory.isEmpty() ? null : equityRaiseHistory.get(equityRaiseHistory.size() - 1).asOfDate();
        LocalDate asOfDate = maxDate(latestBuyback, latestEquityRaise);

        Optional<CapitalAllocationSequenceResult> capitalReturn = engine.evaluateRepeatedCapitalReturn(instrumentId, symbol, buybackHistory, rules);
        Optional<CapitalAllocationSequenceResult> equityRaise = engine.evaluateRepeatedEquityRaise(instrumentId, symbol, equityRaiseHistory, rules);
        boolean anySequenceComplete = isComplete(capitalReturn) || isComplete(equityRaise);

        readinessWriter.write(engine.evaluateReadiness(instrumentId, symbol, asOfDate, buybackHistory, equityRaiseHistory, anySequenceComplete, rules));
        capitalReturn.ifPresent(sequenceWriter::write);
        equityRaise.ifPresent(sequenceWriter::write);
    }

    private List<LocalDate> findAllAsOfDates(UUID instrumentId) {
        TreeSet<LocalDate> dates = new TreeSet<>();
        evidenceReader.findHistory(instrumentId, CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D, null, BACKFILL_DATE_LIMIT).forEach(o -> dates.add(o.asOfDate()));
        evidenceReader.findHistory(instrumentId, CapitalAllocationMetric.EQUITY_RAISE_EVENT_COUNT_180D, null, BACKFILL_DATE_LIMIT).forEach(o -> dates.add(o.asOfDate()));
        return List.copyOf(dates);
    }

    private static boolean isComplete(Optional<CapitalAllocationSequenceResult> result) {
        return result.isPresent() && result.get().sequencePhase() == CapitalAllocationSequencePhase.COMPLETE;
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }
}
