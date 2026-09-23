package com.alphagraph.corporate.transformation;

import com.alphagraph.common.rules.RuleSet;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure calculation - no JDBC, no I/O - same convention as every Stage 2/3 engine. Detects 2
 * transformation sequences (docs/008_Stage3_Sequence_Detection_Specification.md §14.4), explicitly
 * flagged there as "structurally different (event sequences, not state-transition sequences)" -
 * repetition of the *same* signal across genuinely separate real-world corporate actions, not an
 * ordered chain of distinct reason codes like every other domain.
 *
 * <p><b>The core trap - {@code change}, never {@code value}, drives occurrence detection.</b>
 * {@code CapitalAllocationEngine.calculate} recomputes {@code value} fresh every day as a count of
 * an action type's {@code exDate}s inside a rolling 180-day window; {@code change = currentValue -
 * priorValue}. Working the two windows out algebraically: {@code change(d) = (# actions with
 * exDate == d) - (# actions with exDate == d-180)} - so {@code change > 0} fires only on the exact
 * day a qualifying action's {@code exDate} enters the window, never on any of the following up-to-
 * 179 days the same event's shadow keeps {@code value > 0} (and Stage 2's reason firing for all of
 * them). A walker keyed off {@code value > 0} or reason-code presence would misread one real
 * event's 180-day shadow as many repetitions. {@code change} can also be more than 1 on a single
 * row (multiple qualifying actions sharing one {@code exDate}) - {@link #walkRepeatedEvents}
 * registers {@code Math.max(change, 0)} occurrences per row, not one occurrence per {@code change >
 * 0} row.
 *
 * <p><b>Disclosed limitation, not fixed here</b>: {@code change} is a net figure (entries minus
 * exits), not a true entering-event count. If a new qualifying action's {@code exDate} lands
 * exactly 180 days after an earlier one's, the entry (+1) and the exit (-1) cancel and
 * {@code change(d) = 0} - that day's real event becomes invisible to this walker. Broader than one
 * exact-offset coincidence: any same-day combination of entries and exits nets out. The
 * architecturally clean fix is for Stage 1 to eventually persist entering/exiting counts
 * separately, not for Stage 3 to read raw {@code corporate.corporate_actions} directly (which
 * would break the "Stage 3 only reads persisted Stage 1/2 evidence" boundary every domain
 * observes). The same gap means the persisted {@code observedOccurrences} is really "the minimum
 * number of entries inferable from positive net changes," not a guaranteed exact event count -
 * always a conservative undercount, never an overcount.
 *
 * <p><b>Once {@code COMPLETE}, a cluster stays {@code COMPLETE} on further in-window occurrences -
 * it does not reset to {@code FORMING}.</b> A 3rd buyback must never make an already-established
 * repeated-return pattern look *less* mature than it was after the 2nd. {@code observedOccurrences}
 * keeps growing past the threshold while {@code currentStep} stays pinned at the configured
 * repeat count. A fresh attempt starts only once the gap since the last real occurrence exceeds
 * the repeat window - and that same gap-based expiry applies to an already-{@code COMPLETE}
 * cluster too: once genuinely silent for longer than the window, it closes to {@code BROKEN} even
 * with no new occurrence to trigger the check, so a buyback pair from years ago cannot still
 * report as "currently active" forever. Only a cluster that never reached {@code COMPLETE} at all
 * is also subject to the separate, more generous {@code maxAgeDays} stuck-attempt cap.
 *
 * <p><b>No dependency on {@code corporate.inflection_states} at all.</b> Every other domain's
 * Stage 3 reads a Stage 2 state history reader because it needs to merge several *different*
 * reason codes onto one timeline. Capital Allocation's two sequences are each anchored to a single
 * metric - there is no second reason code to merge, and going through Stage 2 would actively lose
 * signal: on a {@code MIXED_CAPITAL_ALLOCATION_ACTIVITY} day the priority-ladder tie-break favors
 * buyback, so equity-raise's own {@code change} for that day is nowhere recorded in
 * {@code corporate.inflection_states}. This engine reads
 * {@code CapitalAllocationEvidenceReader.findHistory} directly, once per metric, independently.
 */
@Component
class CapitalAllocationTransformationSequenceEngine {

    private static final int PERSISTENCE_CAP = 10;
    private static final double STALENESS_PENALTY = 10.0;
    private static final int RULE_VERSION = 1;

    CapitalAllocationSequenceReadinessResult evaluateReadiness(
        UUID instrumentId, String symbol, LocalDate asOfDate,
        List<CapitalAllocationEvidenceObservation> buybackHistory, List<CapitalAllocationEvidenceObservation> equityRaiseHistory,
        boolean anySequenceComplete, RuleSet rules
    ) {
        List<Long> coverageDaysPerMetric = new ArrayList<>();
        coverageDays(buybackHistory).ifPresent(coverageDaysPerMetric::add);
        coverageDays(equityRaiseHistory).ifPresent(coverageDaysPerMetric::add);
        if (coverageDaysPerMetric.isEmpty()) {
            // Structurally unreachable in practice - the orchestrator only calls this for
            // instruments findAllInstrumentIds() already returned real evidence for. Kept for
            // schema symmetry with every other domain's readiness taxonomy.
            return new CapitalAllocationSequenceReadinessResult(instrumentId, symbol, asOfDate, 0, CapitalAllocationSequenceReadiness.MISSING_PREREQUISITE_DATA);
        }
        // The LEAST-observed applicable metric gates readiness, not the best-observed one - a
        // metric with zero evidence ever (that action type never happened for this instrument) is
        // excluded above entirely rather than counted as 0-day coverage, since it isn't "thin
        // data," it's "not applicable."
        long historyDays = Collections.min(coverageDaysPerMetric);
        int repeatWindow = CapitalAllocationSequenceRuleSetLoader.ruleThreshold(rules, "stage3-capital-return-repeat-window", 180);
        CapitalAllocationSequenceReadiness readiness = anySequenceComplete || historyDays >= repeatWindow
            ? CapitalAllocationSequenceReadiness.READY : CapitalAllocationSequenceReadiness.INSUFFICIENT_HISTORY;
        return new CapitalAllocationSequenceReadinessResult(instrumentId, symbol, asOfDate, (int) historyDays, readiness);
    }

    Optional<CapitalAllocationSequenceResult> evaluateRepeatedCapitalReturn(
        UUID instrumentId, String symbol, List<CapitalAllocationEvidenceObservation> buybackHistory, RuleSet rules
    ) {
        if (buybackHistory.isEmpty()) {
            return Optional.empty();
        }
        int requiredRepeats = CapitalAllocationSequenceRuleSetLoader.ruleThreshold(rules, "stage3-capital-return-repeat-min-count", 2);
        int maxGapDays = CapitalAllocationSequenceRuleSetLoader.ruleThreshold(rules, "stage3-capital-return-repeat-window", 180);
        int maxAgeDays = CapitalAllocationSequenceRuleSetLoader.ruleThreshold(rules, "stage3-capital-allocation-sequence-max-age", 540);
        Attempt attempt = walkRepeatedEvents(buybackHistory, requiredRepeats, maxGapDays, maxAgeDays);
        return buildResult(instrumentId, symbol, CapitalAllocationSequenceType.REPEATED_CAPITAL_RETURN, buybackHistory, attempt, requiredRepeats, maxGapDays);
    }

    Optional<CapitalAllocationSequenceResult> evaluateRepeatedEquityRaise(
        UUID instrumentId, String symbol, List<CapitalAllocationEvidenceObservation> equityRaiseHistory, RuleSet rules
    ) {
        if (equityRaiseHistory.isEmpty()) {
            return Optional.empty();
        }
        int requiredRepeats = CapitalAllocationSequenceRuleSetLoader.ruleThreshold(rules, "stage3-equity-raise-repeat-min-count", 2);
        int maxGapDays = CapitalAllocationSequenceRuleSetLoader.ruleThreshold(rules, "stage3-equity-raise-repeat-window", 180);
        int maxAgeDays = CapitalAllocationSequenceRuleSetLoader.ruleThreshold(rules, "stage3-capital-allocation-sequence-max-age", 540);
        Attempt attempt = walkRepeatedEvents(equityRaiseHistory, requiredRepeats, maxGapDays, maxAgeDays);
        return buildResult(instrumentId, symbol, CapitalAllocationSequenceType.REPEATED_EQUITY_RAISE, equityRaiseHistory, attempt, requiredRepeats, maxGapDays);
    }

    // ---- generic repeat-cluster walk ----

    private Attempt walkRepeatedEvents(List<CapitalAllocationEvidenceObservation> historyAscending, int requiredRepeats, int maxGapDays, int maxAgeDays) {
        Attempt attempt = null;
        for (CapitalAllocationEvidenceObservation obs : historyAscending) {
            int enteringEvents = Math.max(obs.change(), 0); // window sliding / an old event aging out (change <= 0) is never a new occurrence
            StepEvidence evidence = occurrenceEvidence(obs);
            for (int n = 0; n < enteringEvents; n++) {
                attempt = registerOccurrence(attempt, obs.asOfDate(), requiredRepeats, maxGapDays, evidence);
            }
        }
        // A COMPLETE cluster is NOT exempt from expiry - it just doesn't reset to FORMING the
        // instant a further occurrence arrives (that's registerOccurrence's job, handled while
        // still inside the window). Once genuinely silent for longer than maxGapDays since the
        // LAST real occurrence, even a COMPLETE cluster must close - otherwise a buyback pair from
        // years ago would report as "currently active" forever. maxAgeDays only matters for a
        // cluster that never reached COMPLETE at all (a stuck FORMING/PROGRESSING attempt going
        // stale with no 2nd occurrence ever arriving).
        if (attempt != null && attempt.phase() != CapitalAllocationSequencePhase.BROKEN) {
            LocalDate latest = historyAscending.get(historyAscending.size() - 1).asOfDate();
            long gapSinceLast = ChronoUnit.DAYS.between(attempt.lastStepEvidenceDate(), latest);
            if (gapSinceLast > maxGapDays) {
                attempt = attempt.close();
            } else if (attempt.phase() != CapitalAllocationSequencePhase.COMPLETE
                && ChronoUnit.DAYS.between(attempt.firstStepEvidenceDate(), latest) > maxAgeDays) {
                attempt = attempt.close();
            }
        }
        return attempt;
    }

    private Attempt registerOccurrence(Attempt attempt, LocalDate occurrenceDate, int requiredRepeats, int maxGapDays, StepEvidence evidence) {
        if (attempt == null || attempt.phase() == CapitalAllocationSequencePhase.BROKEN) {
            return Attempt.start(occurrenceDate, requiredRepeats, evidence);
        }
        long gapDays = ChronoUnit.DAYS.between(attempt.lastStepEvidenceDate(), occurrenceDate);
        if (gapDays > maxGapDays) {
            // Old cluster's window lapsed - this occurrence begins a new one, regardless of
            // whether the old one was FORMING or already COMPLETE.
            return Attempt.start(occurrenceDate, requiredRepeats, evidence);
        }
        // Works identically whether currently FORMING/PROGRESSING (may newly reach COMPLETE) or
        // already COMPLETE (observedOccurrences grows, the phase formula naturally stays COMPLETE
        // since observedOccurrences never decreases).
        return attempt.advance(occurrenceDate, requiredRepeats, evidence);
    }

    // ---- result assembly ----

    private Optional<CapitalAllocationSequenceResult> buildResult(
        UUID instrumentId, String symbol, CapitalAllocationSequenceType sequenceType,
        List<CapitalAllocationEvidenceObservation> history, Attempt attempt, int requiredRepeats, int maxGapDays
    ) {
        if (attempt == null) {
            return Optional.empty();
        }

        LocalDate asOfDate = history.get(history.size() - 1).asOfDate();
        int currentStep = attempt.currentStep(requiredRepeats);

        double confidence = attempt.lastConfidence();
        boolean approachingExpiry = attempt.phase() != CapitalAllocationSequencePhase.COMPLETE
            && ChronoUnit.DAYS.between(attempt.lastStepEvidenceDate(), asOfDate) > maxGapDays / 2.0;
        if (approachingExpiry) {
            confidence -= STALENESS_PENALTY;
        }
        confidence = clamp(confidence, 0.0, 100.0);

        double completionPct = Math.min(100.0, (double) currentStep / requiredRepeats * 100.0);
        double persistencePct = Math.min(100.0, attempt.lastPersistenceDays() / (double) PERSISTENCE_CAP * 100.0);
        double strength = clamp(0.80 * completionPct + 0.20 * persistencePct, 0.0, 100.0);

        List<ReasonCode> reasons = new ArrayList<>();
        if (currentStep >= 1) {
            reasons.add(ReasonCode.of("FIRST_OCCURRENCE_DETECTED"));
        }
        if (currentStep >= 2) {
            reasons.add(ReasonCode.of("SECOND_OCCURRENCE_DETECTED"));
        }
        if (attempt.phase() == CapitalAllocationSequencePhase.COMPLETE) {
            reasons.add(ReasonCode.of("REPEAT_THRESHOLD_REACHED"));
        }
        if (attempt.observedOccurrences() > requiredRepeats) {
            reasons.add(ReasonCode.of("ADDITIONAL_OCCURRENCE_DETECTED", attempt.observedOccurrences()));
        }
        if (attempt.phase() == CapitalAllocationSequencePhase.BROKEN) {
            reasons.add(ReasonCode.of("SEQUENCE_EXPIRED"));
        }

        return Optional.of(new CapitalAllocationSequenceResult(
            instrumentId, symbol, asOfDate, sequenceType, attempt.phase(),
            currentStep, requiredRepeats, attempt.observedOccurrences(), attempt.firstStepEvidenceDate(), attempt.lastStepEvidenceDate(),
            strength, confidence, RULE_VERSION, reasons
        ));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Optional<Long> coverageDays(List<CapitalAllocationEvidenceObservation> history) {
        if (history.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ChronoUnit.DAYS.between(history.get(0).asOfDate(), history.get(history.size() - 1).asOfDate()));
    }

    private static StepEvidence occurrenceEvidence(CapitalAllocationEvidenceObservation obs) {
        return new StepEvidence(obs.asOfDate(), obs.confidence(), obs.persistenceDays());
    }

    // ---- immutable attempt state ----

    private record StepEvidence(LocalDate evidenceDate, double confidence, int persistenceDays) {
    }

    private record Attempt(
        int observedOccurrences, CapitalAllocationSequencePhase phase,
        LocalDate firstStepEvidenceDate, LocalDate lastStepEvidenceDate,
        double lastConfidence, int lastPersistenceDays
    ) {
        static Attempt start(LocalDate occurrenceDate, int requiredRepeats, StepEvidence evidence) {
            CapitalAllocationSequencePhase phase = requiredRepeats <= 1 ? CapitalAllocationSequencePhase.COMPLETE : CapitalAllocationSequencePhase.FORMING;
            return new Attempt(1, phase, occurrenceDate, occurrenceDate, evidence.confidence(), evidence.persistenceDays());
        }

        Attempt advance(LocalDate occurrenceDate, int requiredRepeats, StepEvidence evidence) {
            int newObserved = observedOccurrences + 1;
            CapitalAllocationSequencePhase newPhase = newObserved >= requiredRepeats ? CapitalAllocationSequencePhase.COMPLETE : CapitalAllocationSequencePhase.PROGRESSING;
            return new Attempt(newObserved, newPhase, firstStepEvidenceDate, occurrenceDate, evidence.confidence(), evidence.persistenceDays());
        }

        Attempt close() {
            return new Attempt(observedOccurrences, CapitalAllocationSequencePhase.BROKEN, firstStepEvidenceDate, lastStepEvidenceDate, lastConfidence, lastPersistenceDays);
        }

        int currentStep(int requiredRepeats) {
            return Math.min(observedOccurrences, requiredRepeats);
        }
    }
}
