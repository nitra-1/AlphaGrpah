package com.alphagraph.sector.transformation;

import com.alphagraph.common.rules.RuleSet;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure calculation - no JDBC, no I/O - same convention as every Stage 2/3 engine. Detects 3
 * transformation sequences (docs/008_Stage3_Sequence_Detection_Specification.md §14.5) by walking
 * a real, ascending, as-of-merged Sector Stage 2 history.
 *
 * <p><b>Reason-level evidence dates, never the Stage 2 row's own {@code as_of_date}, drive step
 * ordering and gap-freshness</b> - Sector Stage 2 reads 3 independently-dated metrics per run, and
 * {@code sector.inflection_states.as_of_date} is stamped as whichever metric's own real date drove
 * that row's <i>winning</i> state, never a canonical "same real day across all 3" value. A row's
 * <i>other</i> reason codes can be backed by evidence older than the row's own {@code as_of_date}
 * (e.g. a stale {@code VS_NIFTY} observation that {@code findLatest} keeps returning unchanged every
 * day Stage 2 runs, while {@code SECTOR_RS_RISING} genuinely advances). Treating the row's
 * {@code as_of_date} as when every reason on it occurred would let a stock that was already
 * outperforming Nifty for unrelated reasons weeks before the sector ever strengthened get credited
 * with a fresh, causally-ordered completion - a fabricated sequence. {@link StepEvidence} therefore
 * carries its own real {@code evidenceDate}, and {@link #walkSimpleSequence} only accepts a
 * candidate step as a genuine advance when its evidence date is not older than the attempt's own
 * most-recently-used evidence date - this also prevents one stale observation from manufacturing
 * multiple {@code COMPLETE} results across {@code backfill()}'s per-date replay, since each fresh
 * attempt cycle re-anchors from its own step 1's real evidence date. The <b>persisted</b>
 * {@code firstStepDate}/{@code lastStepDate} come from the attempt's own tracked evidence dates,
 * never {@code history.get(index).asOfDate()}.
 *
 * <p><b>Same-session multi-step advance</b> - {@code SECTOR_LEADERSHIP_CROSSING} fires exactly when
 * {@code VS_SECTOR.value() > 0} <i>and</i> {@code priorValue <= 0}, so whenever it fires,
 * {@code OUTPERFORMING_SECTOR_20D} structurally fires on the <i>same</i> row from the <i>same</i>
 * {@code vsSector} observation. {@link #walkSimpleSequence} greedily advances through as many
 * consecutive steps as the current entry satisfies (each still passing the evidence-date guard),
 * not just one per row - otherwise a one-time crossing event could never satisfy a step that needs
 * it, since it would never recur on a later row.
 *
 * <p><b>{@code IDIOSYNCRATIC_LEADERSHIP} requires real, mutually-fresh evidence, never inferred
 * absence</b> - checking only "the latest row lacks {@code SECTOR_RS_RISING}" conflates "the sector
 * genuinely isn't strengthening" with "we simply have no current sector-level information," since
 * Sector's metrics update independently. {@link #evaluateIdiosyncraticLeadership} reads both raw
 * observations directly, requires both to exist, and requires their own real evidence dates to be
 * within {@code stage3-sector-contrast-max-evidence-lag} of each other before trusting the contrast.
 */
@Component
class SectorTransformationSequenceEngine {

    private static final int MIN_HISTORY_SESSIONS_FOR_READY = 5;
    private static final int PERSISTENCE_CAP = 10;
    private static final double THINNESS_PENALTY = 10.0;
    private static final double STALENESS_PENALTY = 10.0;
    private static final int RULE_VERSION = 1;

    private static final String SECTOR_RS_RISING = "SECTOR_RS_RISING";
    private static final String OUTPERFORMING_NIFTY_20D = "OUTPERFORMING_NIFTY_20D";
    private static final String OUTPERFORMING_SECTOR_20D = "OUTPERFORMING_SECTOR_20D";
    private static final String SECTOR_LEADERSHIP_CROSSING = "SECTOR_LEADERSHIP_CROSSING";

    SectorSequenceReadinessResult evaluateReadiness(UUID instrumentId, String symbol, LocalDate asOfDate, List<SectorInflectionHistoryEntry> historyAscending) {
        int sessions = historyAscending.size();
        SectorSequenceReadiness readiness = sessions >= MIN_HISTORY_SESSIONS_FOR_READY ? SectorSequenceReadiness.READY : SectorSequenceReadiness.INSUFFICIENT_HISTORY;
        return new SectorSequenceReadinessResult(instrumentId, symbol, asOfDate, sessions, readiness);
    }

    Optional<SectorSequenceResult> evaluateSectorTailwindSequence(
        UUID instrumentId, String symbol, List<SectorInflectionHistoryEntry> historyAscending, RuleSet rules
    ) {
        int maxGap = SectorSequenceRuleSetLoader.ruleThreshold(rules, "stage3-sector-tailwind-max-gap", 15);
        int maxAge = SectorSequenceRuleSetLoader.ruleThreshold(rules, "stage3-sector-sequence-max-age", 40);
        List<String> steps = List.of(SECTOR_RS_RISING, OUTPERFORMING_NIFTY_20D);
        Attempt attempt = walkSimpleSequence(historyAscending, steps, maxGap, maxAge);
        return buildResult(instrumentId, symbol, SectorSequenceType.SECTOR_TAILWIND_SEQUENCE, historyAscending, attempt, steps.size(), maxGap);
    }

    Optional<SectorSequenceResult> evaluateStockLeadershipEmergence(
        UUID instrumentId, String symbol, List<SectorInflectionHistoryEntry> historyAscending, RuleSet rules
    ) {
        int maxGap = SectorSequenceRuleSetLoader.ruleThreshold(rules, "stage3-leadership-emergence-max-gap", 30);
        int maxAge = SectorSequenceRuleSetLoader.ruleThreshold(rules, "stage3-sector-sequence-max-age", 40);
        List<String> steps = List.of(SECTOR_RS_RISING, OUTPERFORMING_SECTOR_20D, SECTOR_LEADERSHIP_CROSSING);
        Attempt attempt = walkSimpleSequence(historyAscending, steps, maxGap, maxAge);
        return buildResult(instrumentId, symbol, SectorSequenceType.STOCK_LEADERSHIP_EMERGENCE, historyAscending, attempt, steps.size(), maxGap);
    }

    /**
     * A present-tense contrast, not a chain (confirmed against docs/008's own framing and its §21
     * single-test-case listing for this sequence) - evaluated only against the single latest entry.
     */
    Optional<SectorSequenceResult> evaluateIdiosyncraticLeadership(
        UUID instrumentId, String symbol, List<SectorInflectionHistoryEntry> historyAscending, RuleSet rules
    ) {
        if (historyAscending.isEmpty()) {
            return Optional.empty();
        }
        int lastIndex = historyAscending.size() - 1;
        SectorInflectionHistoryEntry latest = historyAscending.get(lastIndex);
        SectorEvidenceObservation vsSector = latest.vsSector();
        SectorEvidenceObservation sectorRs = latest.sectorRelativeStrength();
        if (vsSector == null || sectorRs == null) {
            return Optional.empty(); // can't establish the contrast without both real observations
        }
        boolean outperforming = vsSector.value() != null && vsSector.value().signum() > 0;
        // A null change means genuinely no computable trend yet (real evidence, just not enough of
        // it) - treated as "not confirmed rising," distinct from sectorRs itself being entirely null.
        boolean sectorNotRising = sectorRs.change() == null || sectorRs.change().signum() <= 0;
        if (!outperforming || !sectorNotRising) {
            return Optional.empty();
        }
        int maxLagSessions = SectorSequenceRuleSetLoader.ruleThreshold(rules, "stage3-sector-contrast-max-evidence-lag", 5);
        long lagDays = Math.abs(ChronoUnit.DAYS.between(sectorRs.asOfDate(), vsSector.asOfDate()));
        if (lagDays > maxLagSessions) {
            return Optional.empty(); // evidence too far apart in time to trust the contrast
        }

        StepEvidence evidence = evidenceFor(OUTPERFORMING_SECTOR_20D, latest);
        Attempt attempt = Attempt.startComplete(lastIndex, evidence);
        return buildResult(instrumentId, symbol, SectorSequenceType.IDIOSYNCRATIC_LEADERSHIP, historyAscending, attempt, 1, 0);
    }

    // ---- generic attempt-lifecycle walk, with the evidence-date guard and same-session multi-advance ----

    private Attempt walkSimpleSequence(List<SectorInflectionHistoryEntry> history, List<String> stepReasonCodes, int maxGapSessions, int maxAgeSessions) {
        int totalSteps = stepReasonCodes.size();
        Attempt attempt = null;
        for (int i = 0; i < history.size(); i++) {
            SectorInflectionHistoryEntry entry = history.get(i);
            boolean terminal = attempt == null || attempt.phase() == SectorSequencePhase.COMPLETE || attempt.phase() == SectorSequencePhase.BROKEN;

            if (terminal) {
                if (!entry.hasReason(stepReasonCodes.get(0))) {
                    continue;
                }
                attempt = Attempt.start(i, evidenceFor(stepReasonCodes.get(0), entry));
            }

            // Keep advancing through as many further steps as this SAME entry also satisfies
            // (Decision 2), each transition still passing the evidence-date freshness guard
            // (Decision 1).
            while (attempt.currentStep() < totalSteps && entry.hasReason(stepReasonCodes.get(attempt.currentStep()))) {
                StepEvidence evidence = evidenceFor(stepReasonCodes.get(attempt.currentStep()), entry);
                if (attempt.lastStepEvidenceDate() != null && evidence.evidenceDate() != null
                    && evidence.evidenceDate().isBefore(attempt.lastStepEvidenceDate())) {
                    break; // stale evidence for the next step - don't let it fake progression
                }
                attempt = attempt.advance(i, totalSteps, evidence);
            }

            if (attempt.phase() != SectorSequencePhase.COMPLETE) {
                int gap = i - attempt.lastStepIndex();
                if (gap > maxGapSessions || (i - attempt.firstIndex()) > maxAgeSessions) {
                    attempt = attempt.close();
                }
            }
        }
        return attempt;
    }

    // ---- result assembly ----

    private Optional<SectorSequenceResult> buildResult(
        UUID instrumentId, String symbol, SectorSequenceType sequenceType,
        List<SectorInflectionHistoryEntry> history, Attempt attempt, int totalSteps, int maxGapSessions
    ) {
        if (attempt == null) {
            return Optional.empty();
        }

        LocalDate asOfDate = history.get(history.size() - 1).asOfDate();
        // Real underlying evidence dates, never the Stage 2 row's own as_of_date (Decision 1).
        LocalDate firstStepDate = attempt.firstStepEvidenceDate();
        LocalDate lastStepDate = attempt.lastStepEvidenceDate();

        double confidence = weightedConfidence(attempt, totalSteps);
        boolean approachingExpiry = attempt.phase() != SectorSequencePhase.COMPLETE
            && (history.size() - 1 - attempt.lastStepIndex()) > maxGapSessions / 2.0;
        if (approachingExpiry) {
            confidence -= STALENESS_PENALTY;
        }
        confidence = clamp(confidence, 0.0, 100.0);

        double completionPct = (double) attempt.currentStep() / totalSteps * 100.0;
        double persistencePct = Math.min(100.0, attempt.stepPersistenceDays().get(attempt.stepPersistenceDays().size() - 1) / (double) PERSISTENCE_CAP * 100.0);
        double strength = clamp(0.80 * completionPct + 0.20 * persistencePct, 0.0, 100.0);

        List<ReasonCode> reasons = new ArrayList<>();
        if (attempt.currentStep() >= 1) {
            reasons.add(ReasonCode.of("FIRST_STEP_DETECTED"));
        }
        if (attempt.currentStep() >= 2) {
            reasons.add(ReasonCode.of("SECOND_STEP_DETECTED"));
        }
        if (attempt.phase() == SectorSequencePhase.COMPLETE) {
            reasons.add(ReasonCode.of("ALL_REQUIRED_STEPS_COMPLETE"));
        }
        if (attempt.phase() == SectorSequencePhase.BROKEN) {
            reasons.add(ReasonCode.of("SEQUENCE_EXPIRED"));
        }

        return Optional.of(new SectorSequenceResult(
            instrumentId, symbol, asOfDate, sequenceType, attempt.phase(),
            attempt.currentStep(), totalSteps, firstStepDate, lastStepDate,
            strength, confidence, RULE_VERSION, reasons
        ));
    }

    private static double weightedConfidence(Attempt attempt, int totalSteps) {
        double[] fullWeights = totalSteps == 2 ? new double[] {0.4, 0.6} : new double[] {0.2, 0.3, 0.5};
        int completed = attempt.stepConfidences().size();
        double weightSum = 0;
        for (int i = 0; i < completed; i++) {
            weightSum += fullWeights[i];
        }
        double weighted = 0;
        for (int i = 0; i < completed; i++) {
            weighted += attempt.stepConfidences().get(i) * (fullWeights[i] / weightSum);
        }
        boolean lastStepHasPrior = attempt.stepHasPrior().get(completed - 1);
        return lastStepHasPrior ? weighted : weighted - THINNESS_PENALTY;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---- reason code -> underlying Stage 1 metric evidence mapping ----

    private record StepEvidence(LocalDate evidenceDate, double confidence, int persistenceDays, boolean hasPrior) {
    }

    private static StepEvidence evidenceFor(String reasonCode, SectorInflectionHistoryEntry entry) {
        return switch (reasonCode) {
            case SECTOR_RS_RISING -> fromObservation(entry.sectorRelativeStrength());
            case OUTPERFORMING_NIFTY_20D -> fromObservation(entry.vsNifty());
            case OUTPERFORMING_SECTOR_20D, SECTOR_LEADERSHIP_CROSSING -> fromObservation(entry.vsSector());
            default -> throw new IllegalArgumentException("Unknown reason code: " + reasonCode);
        };
    }

    private static StepEvidence fromObservation(SectorEvidenceObservation observation) {
        if (observation == null) {
            // Defensive only - a reason code firing structurally implies its underlying evidence
            // exists; this should never actually happen against real data.
            return new StepEvidence(null, 0.0, 0, false);
        }
        return new StepEvidence(observation.asOfDate(), observation.confidence(), observation.persistenceDays(), observation.priorAsOfDate() != null);
    }

    // ---- immutable attempt state ----

    private record Attempt(
        int currentStep, int firstIndex, int lastStepIndex, SectorSequencePhase phase,
        LocalDate firstStepEvidenceDate, LocalDate lastStepEvidenceDate,
        List<Double> stepConfidences, List<Integer> stepPersistenceDays, List<Boolean> stepHasPrior
    ) {
        static Attempt start(int index, StepEvidence evidence) {
            return new Attempt(1, index, index, SectorSequencePhase.FORMING, evidence.evidenceDate(), evidence.evidenceDate(),
                List.of(evidence.confidence()), List.of(evidence.persistenceDays()), List.of(evidence.hasPrior()));
        }

        /** Used only by the 1-step {@code IDIOSYNCRATIC_LEADERSHIP} detector - no step 2 to advance() into. */
        static Attempt startComplete(int index, StepEvidence evidence) {
            return new Attempt(1, index, index, SectorSequencePhase.COMPLETE, evidence.evidenceDate(), evidence.evidenceDate(),
                List.of(evidence.confidence()), List.of(evidence.persistenceDays()), List.of(evidence.hasPrior()));
        }

        Attempt advance(int index, int totalSteps, StepEvidence evidence) {
            int newStep = currentStep + 1;
            SectorSequencePhase newPhase = newStep == totalSteps ? SectorSequencePhase.COMPLETE : SectorSequencePhase.PROGRESSING;
            return new Attempt(newStep, firstIndex, index, newPhase, firstStepEvidenceDate, evidence.evidenceDate(),
                append(stepConfidences, evidence.confidence()), append(stepPersistenceDays, evidence.persistenceDays()), append(stepHasPrior, evidence.hasPrior()));
        }

        Attempt close() {
            return new Attempt(currentStep, firstIndex, lastStepIndex, SectorSequencePhase.BROKEN, firstStepEvidenceDate, lastStepEvidenceDate,
                stepConfidences, stepPersistenceDays, stepHasPrior);
        }

        private static <T> List<T> append(List<T> list, T value) {
            List<T> copy = new ArrayList<>(list);
            copy.add(value);
            return List.copyOf(copy);
        }
    }
}
