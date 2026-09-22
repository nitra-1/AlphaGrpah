package com.alphagraph.financial.transformation;

import com.alphagraph.common.inflection.VelocityBand;
import com.alphagraph.common.rules.RuleSet;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Pure calculation - no JDBC, no I/O - same convention as every Stage 2/3 engine. Detects 4
 * transformation sequences (docs/008_Stage3_Sequence_Detection_Specification.md §14.1) by walking
 * a real, ascending Financial Stage 2 history. A 5th group ({@code DELEVERAGING_CYCLE}/
 * {@code BALANCE_SHEET_REPAIR}/{@code CASH_FLOW_TURNAROUND}) stays reserved, not implemented here -
 * no {@code DEBT_LEVEL}/{@code CASH_FLOW_FROM_OPERATIONS} Stage 1 evidence exists yet.
 *
 * <p><b>"Acceleration" is a Stage-2-derived concept, not Stage-1 evidence</b> -
 * {@code FinancialInflectionEngine.computeAcceleration()} (Stage 2's own private method) rebuilds a
 * growth-rate series and computes whether growth is itself accelerating (a 2nd-derivative concept),
 * with its own persistence counter. This is never stored in Stage 1's own evidence
 * ({@code financial.transformation_evidence.persistence_quarters} is a separate, 1st-derivative
 * "consecutive quarters of positive growth" count) - so step confidence/persistence here come from
 * each metric's own real Stage 1 evidence directly (never re-deriving Stage 2's private algorithm),
 * <b>except</b> {@link #BUSINESS_ACCELERATION_CYCLE}'s velocity gate, which has no Stage-1
 * equivalent at all and reads the real, already-persisted {@code metric_value} Stage 2 stored on
 * the {@code REVENUE_GROWTH_IMPROVING} reason directly (the acceleration delta itself) - the one
 * place this engine reads a Stage-2 number instead of Stage-1 evidence, and correct precisely
 * because no Stage-1 equivalent exists. Do not "fix" this into a {@link StepEvidence} call by
 * analogy with the other three sequences.
 *
 * <p><b>{@code MULTI_QUARTER_EARNINGS_EXPANSION} tracks PAT and margin as two fully independent
 * persistence streaks, never combined via OR</b> - alternating metrics (PAT one quarter, margin the
 * next) must never manufacture persistence neither metric actually has. Implemented as two
 * unmodified calls to the same {@link #walkPersistenceChain} plus a simple winner pick, not a
 * bespoke 3rd walker.
 *
 * <p><b>No point-in-time-safe backfill is exposed for Financial</b> - {@code as_of_date} is the
 * quarter-<i>end</i> date (real information didn't exist until results were actually filed, weeks
 * later - no {@code available_from}/{@code result_publication_date} field exists upstream yet, same
 * disclosed gap docs/007/docs/008 already carry). {@code run()} (forward-only, using whatever is
 * currently latest) is unaffected; the orchestrator's own {@code backfill()} method exists (for
 * tests, and future activation) but is deliberately not wired to any runnable job in this build -
 * see {@code FinancialTransformationSequenceOrchestrator}'s own javadoc.
 */
@Component
class FinancialTransformationSequenceEngine {

    private static final int MIN_HISTORY_PERIODS_FOR_READY = 3;
    private static final int PERSISTENCE_CAP = 4;
    private static final double THINNESS_PENALTY = 10.0;
    private static final double STALENESS_PENALTY = 10.0;
    private static final int RULE_VERSION = 1;

    private static final String REVENUE_GROWTH_IMPROVING = "REVENUE_GROWTH_IMPROVING";
    private static final String PAT_GROWTH_IMPROVING = "PAT_GROWTH_IMPROVING";
    private static final String MARGIN_EXPANDING_SUSTAINED = "MARGIN_EXPANDING_SUSTAINED";
    private static final String INTEREST_EXPENSE_FALLING = "INTEREST_EXPENSE_FALLING";

    FinancialSequenceReadinessResult evaluateReadiness(UUID instrumentId, String symbol, LocalDate asOfDate, List<FinancialInflectionHistoryEntry> historyAscending) {
        int periods = historyAscending.size();
        FinancialSequenceReadiness readiness = periods >= MIN_HISTORY_PERIODS_FOR_READY ? FinancialSequenceReadiness.READY : FinancialSequenceReadiness.INSUFFICIENT_HISTORY;
        return new FinancialSequenceReadinessResult(instrumentId, symbol, asOfDate, periods, readiness);
    }

    Optional<FinancialSequenceResult> evaluateBusinessAccelerationCycle(
        UUID instrumentId, String symbol, List<FinancialInflectionHistoryEntry> historyAscending, RuleSet rules
    ) {
        int maxGap = FinancialSequenceRuleSetLoader.ruleThreshold(rules, "stage3-business-acceleration-max-gap", 0);
        int maxAge = FinancialSequenceRuleSetLoader.ruleThreshold(rules, "stage3-financial-sequence-max-age", 6);
        BiPredicate<FinancialInflectionHistoryEntry, Integer> velocityGate = (entry, step) -> {
            Double delta = entry.metricValueFor(REVENUE_GROWTH_IMPROVING);
            if (delta == null) {
                return false;
            }
            VelocityBand band = FinancialVelocityBanding.bandPercentagePoint(BigDecimal.valueOf(delta));
            return band == VelocityBand.MODERATE || band == VelocityBand.STRONG;
        };
        Attempt attempt = walkPersistenceChain(historyAscending, REVENUE_GROWTH_IMPROVING, 3, maxGap, maxAge, velocityGate);
        return buildResult(instrumentId, symbol, FinancialSequenceType.BUSINESS_ACCELERATION_CYCLE, historyAscending, attempt, 3, List.of(REVENUE_GROWTH_IMPROVING));
    }

    Optional<FinancialSequenceResult> evaluateOperatingLeverageCycle(
        UUID instrumentId, String symbol, List<FinancialInflectionHistoryEntry> historyAscending, RuleSet rules
    ) {
        int maxQuarterGap = FinancialSequenceRuleSetLoader.ruleThreshold(rules, "stage3-earnings-cycle-max-quarter-gap", 4);
        Attempt attempt = walkOperatingLeverageSequence(historyAscending, maxQuarterGap);
        return buildResult(instrumentId, symbol, FinancialSequenceType.OPERATING_LEVERAGE_CYCLE, historyAscending, attempt, 3,
            List.of(REVENUE_GROWTH_IMPROVING, MARGIN_EXPANDING_SUSTAINED, PAT_GROWTH_IMPROVING));
    }

    Optional<FinancialSequenceResult> evaluateMultiQuarterEarningsExpansion(
        UUID instrumentId, String symbol, List<FinancialInflectionHistoryEntry> historyAscending, RuleSet rules
    ) {
        int minPersistence = FinancialSequenceRuleSetLoader.ruleThreshold(rules, "stage3-multi-quarter-earnings-min-persistence", 2);
        int maxAge = FinancialSequenceRuleSetLoader.ruleThreshold(rules, "stage3-financial-sequence-max-age", 6);
        Attempt patAttempt = walkPersistenceChain(historyAscending, PAT_GROWTH_IMPROVING, minPersistence, 0, maxAge, null);
        Attempt marginAttempt = walkPersistenceChain(historyAscending, MARGIN_EXPANDING_SUSTAINED, minPersistence, 0, maxAge, null);
        Attempt winning = pickWinningAttempt(patAttempt, marginAttempt);
        return buildResult(instrumentId, symbol, FinancialSequenceType.MULTI_QUARTER_EARNINGS_EXPANSION, historyAscending, winning, minPersistence,
            List.of(PAT_GROWTH_IMPROVING, MARGIN_EXPANDING_SUSTAINED));
    }

    Optional<FinancialSequenceResult> evaluateInterestCostReliefTrend(
        UUID instrumentId, String symbol, List<FinancialInflectionHistoryEntry> historyAscending, RuleSet rules
    ) {
        int maxGap = FinancialSequenceRuleSetLoader.ruleThreshold(rules, "stage3-interest-relief-max-gap", 0);
        int maxAge = FinancialSequenceRuleSetLoader.ruleThreshold(rules, "stage3-financial-sequence-max-age", 6);
        Attempt attempt = walkPersistenceChain(historyAscending, INTEREST_EXPENSE_FALLING, 3, maxGap, maxAge, null);
        return buildResult(instrumentId, symbol, FinancialSequenceType.INTEREST_COST_RELIEF_TREND, historyAscending, attempt, 3, List.of(INTEREST_EXPENSE_FALLING));
    }

    // ---- generic repeated-condition persistence chain (serves 3 of 4 sequences, twice each for the 4th) ----

    private Attempt walkPersistenceChain(
        List<FinancialInflectionHistoryEntry> history, String reasonCode,
        int totalSteps, int maxGapPeriods, int maxAgePeriods, BiPredicate<FinancialInflectionHistoryEntry, Integer> finalStepGateOrNull
    ) {
        Attempt attempt = null;
        for (int i = 0; i < history.size(); i++) {
            FinancialInflectionHistoryEntry entry = history.get(i);
            boolean terminal = attempt == null || attempt.phase() == FinancialSequencePhase.COMPLETE || attempt.phase() == FinancialSequencePhase.BROKEN;

            if (terminal) {
                if (entry.hasReason(reasonCode)) {
                    attempt = Attempt.start(i, evidenceFor(entry, reasonCode));
                }
                continue;
            }

            boolean matched = false;
            if (entry.hasReason(reasonCode)) {
                int nextStep = attempt.currentStep() + 1;
                boolean isFinalTransition = nextStep == totalSteps;
                boolean gatePasses = finalStepGateOrNull == null || !isFinalTransition || finalStepGateOrNull.test(entry, nextStep);
                if (gatePasses) {
                    attempt = attempt.advance(i, totalSteps, evidenceFor(entry, reasonCode));
                    matched = true;
                }
                // Gate failed on what would be the final step: falls through as a non-match, same as silence.
            }
            if (!matched) {
                int gap = i - attempt.lastStepIndex();
                if (gap > maxGapPeriods || (i - attempt.firstIndex()) > maxAgePeriods) {
                    attempt = attempt.close();
                }
            }
        }
        return attempt;
    }

    /** Prefer a COMPLETE attempt; between two COMPLETE, whichever completed earlier; between two incomplete, whichever is more progressed. */
    private static Attempt pickWinningAttempt(Attempt a, Attempt b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        boolean aComplete = a.phase() == FinancialSequencePhase.COMPLETE;
        boolean bComplete = b.phase() == FinancialSequencePhase.COMPLETE;
        if (aComplete != bComplete) {
            return aComplete ? a : b;
        }
        if (a.currentStep() != b.currentStep()) {
            return a.currentStep() > b.currentStep() ? a : b;
        }
        return a.lastStepIndex() <= b.lastStepIndex() ? a : b;
    }

    // ---- anchor-then-pair: OPERATING_LEVERAGE_CYCLE only ----

    /**
     * Revenue must start the attempt. Once started, Margin/PAT's own first-occurrence indices are
     * latched (from {@code firstIndex} onward only - an occurrence before revenue started can never
     * retroactively count). Unlike Market's {@code stealthPrerequisitePairings}, there is no
     * per-anchor independent expiry - the doc ties the whole window to revenue's own start as a
     * single outer bound; once Margin or PAT is seen it's latched permanently for this attempt.
     */
    private Attempt walkOperatingLeverageSequence(List<FinancialInflectionHistoryEntry> history, int maxQuarterGap) {
        Attempt attempt = null;
        Integer marginIndex = null;
        Integer patIndex = null;

        for (int i = 0; i < history.size(); i++) {
            FinancialInflectionHistoryEntry entry = history.get(i);
            boolean terminal = attempt == null || attempt.phase() == FinancialSequencePhase.COMPLETE || attempt.phase() == FinancialSequencePhase.BROKEN;

            if (terminal) {
                if (entry.hasReason(REVENUE_GROWTH_IMPROVING)) {
                    attempt = Attempt.start(i, evidenceFor(entry, REVENUE_GROWTH_IMPROVING));
                    marginIndex = null;
                    patIndex = null;
                } else {
                    continue;
                }
            }

            if (marginIndex == null && entry.hasReason(MARGIN_EXPANDING_SUSTAINED)) {
                marginIndex = i;
            }
            if (patIndex == null && entry.hasReason(PAT_GROWTH_IMPROVING)) {
                patIndex = i;
            }

            if (attempt.currentStep() == 1 && (marginIndex != null || patIndex != null)) {
                boolean marginFirst = marginIndex != null && (patIndex == null || marginIndex <= patIndex);
                String firstCode = marginFirst ? MARGIN_EXPANDING_SUSTAINED : PAT_GROWTH_IMPROVING;
                int firstArrivalIndex = marginFirst ? marginIndex : patIndex;
                attempt = attempt.advance(firstArrivalIndex, 3, evidenceFor(history.get(firstArrivalIndex), firstCode));
            }

            if (attempt.currentStep() == 2 && marginIndex != null && patIndex != null) {
                boolean marginLast = marginIndex > patIndex;
                String secondCode = marginLast ? MARGIN_EXPANDING_SUSTAINED : PAT_GROWTH_IMPROVING;
                int secondArrivalIndex = Math.max(marginIndex, patIndex);
                attempt = attempt.advance(secondArrivalIndex, 3, evidenceFor(history.get(secondArrivalIndex), secondCode));
                // advance() auto-completes since newStep(3) == totalSteps(3)
            }

            if (attempt.phase() != FinancialSequencePhase.COMPLETE && (i - attempt.firstIndex()) > maxQuarterGap) {
                attempt = attempt.close();
            }
        }
        return attempt;
    }

    // ---- result assembly ----

    private Optional<FinancialSequenceResult> buildResult(
        UUID instrumentId, String symbol, FinancialSequenceType sequenceType,
        List<FinancialInflectionHistoryEntry> history, Attempt attempt, int totalSteps, List<String> disambiguationCodes
    ) {
        if (attempt == null) {
            return Optional.empty();
        }

        LocalDate asOfDate = history.get(history.size() - 1).asOfDate();
        LocalDate firstStepDate = history.get(attempt.firstIndex()).asOfDate();
        LocalDate lastStepDate = history.get(attempt.lastStepIndex()).asOfDate();

        double confidence = weightedConfidence(attempt);
        boolean approachingExpiry = attempt.phase() != FinancialSequencePhase.COMPLETE
            && (history.size() - 1 - attempt.lastStepIndex()) > 1;
        if (approachingExpiry) {
            confidence -= STALENESS_PENALTY;
        }
        confidence = clamp(confidence, 0.0, 100.0);

        double completionPct = (double) attempt.currentStep() / totalSteps * 100.0;
        double persistencePct = Math.min(100.0, attempt.stepPersistenceQuarters().get(attempt.stepPersistenceQuarters().size() - 1) / (double) PERSISTENCE_CAP * 100.0);
        double strength = clamp(0.80 * completionPct + 0.20 * persistencePct, 0.0, 100.0);

        List<ReasonCode> reasons = new ArrayList<>();
        if (attempt.currentStep() >= 1) {
            reasons.add(ReasonCode.of("FIRST_STEP_DETECTED"));
        }
        if (attempt.currentStep() >= 2) {
            reasons.add(ReasonCode.of("SECOND_STEP_DETECTED"));
        }
        if (attempt.phase() == FinancialSequencePhase.COMPLETE) {
            reasons.add(ReasonCode.of("ALL_REQUIRED_STEPS_COMPLETE"));
        }
        if (attempt.phase() == FinancialSequencePhase.BROKEN) {
            reasons.add(ReasonCode.of("SEQUENCE_EXPIRED"));
        }
        // Scans every index across the attempt's own span, not just the two endpoints - a 3-step
        // sequence's middle occurrence (e.g. OPERATING_LEVERAGE_CYCLE's margin step) would otherwise
        // never be checked at all.
        for (String code : disambiguationCodes) {
            for (int idx = attempt.firstIndex(); idx <= attempt.lastStepIndex(); idx++) {
                if (history.get(idx).hasReason(code)) {
                    reasons.add(ReasonCode.of(code));
                    break;
                }
            }
        }

        return Optional.of(new FinancialSequenceResult(
            instrumentId, symbol, asOfDate, sequenceType, attempt.phase(),
            attempt.currentStep(), totalSteps, firstStepDate, lastStepDate,
            strength, confidence, RULE_VERSION, reasons
        ));
    }

    /** Flat average of captured step confidences - every occurrence in a persistence-style chain is equally informative, unlike Market's differently-weighted ordered evidence types. */
    private static double weightedConfidence(Attempt attempt) {
        int completed = attempt.stepConfidences().size();
        double sum = 0;
        for (double confidence : attempt.stepConfidences()) {
            sum += confidence;
        }
        double average = sum / completed;
        boolean lastStepHasPrior = attempt.stepHasPrior().get(completed - 1);
        return lastStepHasPrior ? average : average - THINNESS_PENALTY;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---- reason code -> underlying Stage 1 metric evidence mapping ----

    private record StepEvidence(double confidence, int persistenceQuarters, boolean hasPrior) {
    }

    private static StepEvidence evidenceFor(FinancialInflectionHistoryEntry entry, String reasonCode) {
        return switch (reasonCode) {
            case REVENUE_GROWTH_IMPROVING -> fromObservation(entry.revenue());
            case PAT_GROWTH_IMPROVING -> fromObservation(entry.pat());
            case MARGIN_EXPANDING_SUSTAINED -> fromObservation(entry.margin());
            case INTEREST_EXPENSE_FALLING -> fromObservation(entry.interest());
            default -> throw new IllegalArgumentException("Unknown reason code: " + reasonCode);
        };
    }

    private static StepEvidence fromObservation(FinancialEvidenceObservation observation) {
        if (observation == null) {
            // Defensive only - a reason code firing structurally implies its underlying exact-period_end
            // evidence exists (all 4 metrics share one FinancialResultsPeriod per quarter); should
            // never actually happen against real data.
            return new StepEvidence(0.0, 0, false);
        }
        return new StepEvidence(observation.confidence(), observation.persistenceQuarters(), observation.priorPeriodEnd() != null);
    }

    // ---- immutable attempt state ----

    private record Attempt(
        int currentStep, int firstIndex, int lastStepIndex, FinancialSequencePhase phase,
        List<Double> stepConfidences, List<Integer> stepPersistenceQuarters, List<Boolean> stepHasPrior
    ) {
        static Attempt start(int index, StepEvidence evidence) {
            return new Attempt(1, index, index, FinancialSequencePhase.FORMING,
                List.of(evidence.confidence()), List.of(evidence.persistenceQuarters()), List.of(evidence.hasPrior()));
        }

        /** Auto-completes when reaching {@code totalSteps} - correct for every Financial sequence (none has an Ownership-style "reach the last step but need more before real completion" semantic). */
        Attempt advance(int index, int totalSteps, StepEvidence evidence) {
            int newStep = currentStep + 1;
            FinancialSequencePhase newPhase = newStep == totalSteps ? FinancialSequencePhase.COMPLETE : FinancialSequencePhase.PROGRESSING;
            return new Attempt(newStep, firstIndex, index, newPhase,
                append(stepConfidences, evidence.confidence()), append(stepPersistenceQuarters, evidence.persistenceQuarters()), append(stepHasPrior, evidence.hasPrior()));
        }

        Attempt close() {
            return new Attempt(currentStep, firstIndex, lastStepIndex, FinancialSequencePhase.BROKEN, stepConfidences, stepPersistenceQuarters, stepHasPrior);
        }

        private static <T> List<T> append(List<T> list, T value) {
            List<T> copy = new ArrayList<>(list);
            copy.add(value);
            return List.copyOf(copy);
        }
    }
}
