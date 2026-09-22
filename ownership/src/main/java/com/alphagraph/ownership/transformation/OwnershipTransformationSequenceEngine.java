package com.alphagraph.ownership.transformation;

import com.alphagraph.common.rules.RuleSet;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure calculation - no JDBC, no I/O - same convention as every Stage 2/3 engine. Detects 3
 * transformation sequences (docs/008_Stage3_Sequence_Detection_Specification.md §14.2) by walking
 * a real, ascending, <b>canonicalized-by-distinct-quarter</b> Ownership Stage 2 history (see
 * {@link OwnershipInflectionHistoryReader}'s own javadoc for why raw state rows are never walked
 * directly - Correction 1).
 *
 * <p><b>Step detection uses reason codes, never {@code primary_state}</b> - same lesson every prior
 * Stage 3 engine already established, doubly true here since {@code OwnershipTransformationEngine}'s
 * own priority ladder can rank {@code OWNERSHIP_CONTRADICTION} above a genuinely co-occurring
 * {@code INSTITUTIONAL_OWNERSHIP_EXPANSION} in the very same quarter.
 *
 * <p><b>Contradiction and progression are fully orthogonal</b> (Correction 2): a single quarter can
 * legitimately carry both {@code INSTITUTIONAL_OWNERSHIP_EXPANSION} and
 * {@code OWNERSHIP_CONTRADICTION} reason codes at once (verified against
 * {@code OwnershipTransformationEngine.bandStates()}: {@code OWNERSHIP_CONTRADICTION} fires whenever
 * {@code (fiiUp || diiUp) && promoterDown}, independently of {@code EXPANSION}'s own
 * {@code fiiUp && diiUp} trigger - both can hold together). {@code walkOwnershipBuildingSequence}
 * therefore runs a progression pass and a contradiction pass as two fully independent steps every
 * period - the contradiction streak is never reset merely because the sequence also advanced.
 *
 * <p><b>Composite-state evidence resolves from Stage 2's own {@code driving_metric}</b> (Correction
 * 3), not an unconditional {@code min(FII, DII)} - see {@link #fromDrivingMetric}.
 *
 * <p><b>The two pairing sequences persist {@code FORMING}</b> the moment exactly one side is
 * pending (Correction 4) - Stage 3's purpose is exposing transformations while developing, not only
 * once complete.
 */
@Component
class OwnershipTransformationSequenceEngine {

    private static final int MIN_HISTORY_PERIODS_FOR_READY = 3;
    private static final int PERSISTENCE_CAP = 4;
    private static final double THINNESS_PENALTY = 10.0;
    private static final double STALENESS_PENALTY = 10.0;
    private static final int RULE_VERSION = 1;

    private static final String FII_ACCUMULATION = "FII_ACCUMULATION";
    private static final String DII_ACCUMULATION = "DII_ACCUMULATION";
    private static final String PROMOTER_HOLDING_INCREASE = "PROMOTER_HOLDING_INCREASE";
    private static final String INSTITUTIONAL_OWNERSHIP_EXPANSION = "INSTITUTIONAL_OWNERSHIP_EXPANSION";
    private static final String BULK_BUYING_WITH_OWNERSHIP_EXPANSION = "BULK_BUYING_WITH_OWNERSHIP_EXPANSION";
    private static final String OWNERSHIP_CONTRADICTION = "OWNERSHIP_CONTRADICTION";

    private static final List<String> ALIGNMENT_OTHER_CODES = List.of(FII_ACCUMULATION, DII_ACCUMULATION, INSTITUTIONAL_OWNERSHIP_EXPANSION);

    OwnershipSequenceReadinessResult evaluateReadiness(UUID instrumentId, String symbol, LocalDate asOfDate, List<OwnershipInflectionHistoryEntry> historyAscending) {
        int periods = historyAscending.size();
        OwnershipSequenceReadiness readiness = periods >= MIN_HISTORY_PERIODS_FOR_READY ? OwnershipSequenceReadiness.READY : OwnershipSequenceReadiness.INSUFFICIENT_HISTORY;
        return new OwnershipSequenceReadinessResult(instrumentId, symbol, asOfDate, periods, readiness);
    }

    Optional<OwnershipSequenceResult> evaluateInstitutionalOwnershipBuilding(
        UUID instrumentId, String symbol, List<OwnershipInflectionHistoryEntry> historyAscending, RuleSet rules
    ) {
        int maxGap = OwnershipSequenceRuleSetLoader.ruleThreshold(rules, "stage3-ownership-building-max-gap", 2);
        int minPersistence = OwnershipSequenceRuleSetLoader.ruleThreshold(rules, "stage3-ownership-building-min-persistence", 2);
        int contradictionTolerance = OwnershipSequenceRuleSetLoader.ruleThreshold(rules, "stage3-ownership-contradiction-tolerance", 2);
        int maxAge = OwnershipSequenceRuleSetLoader.ruleThreshold(rules, "stage3-ownership-sequence-max-age", 6);
        Attempt attempt = walkOwnershipBuildingSequence(historyAscending, maxGap, minPersistence, contradictionTolerance, maxAge);
        return buildResult(instrumentId, symbol, OwnershipSequenceType.INSTITUTIONAL_OWNERSHIP_BUILDING, historyAscending, attempt, 2, maxGap, List.of(FII_ACCUMULATION, DII_ACCUMULATION));
    }

    Optional<OwnershipSequenceResult> evaluateBroadInstitutionalParticipation(
        UUID instrumentId, String symbol, List<OwnershipInflectionHistoryEntry> historyAscending, RuleSet rules
    ) {
        int maxSpan = OwnershipSequenceRuleSetLoader.ruleThreshold(rules, "stage3-broad-participation-max-span", 2);
        int maxAge = OwnershipSequenceRuleSetLoader.ruleThreshold(rules, "stage3-ownership-sequence-max-age", 6);
        Attempt attempt = walkPairingSequence(historyAscending, maxSpan, maxAge, FII_ACCUMULATION, List.of(DII_ACCUMULATION));
        return buildResult(instrumentId, symbol, OwnershipSequenceType.BROAD_INSTITUTIONAL_PARTICIPATION, historyAscending, attempt, 2, maxSpan, List.of(FII_ACCUMULATION, DII_ACCUMULATION));
    }

    Optional<OwnershipSequenceResult> evaluatePromoterInstitutionAlignment(
        UUID instrumentId, String symbol, List<OwnershipInflectionHistoryEntry> historyAscending, RuleSet rules
    ) {
        int maxSpan = OwnershipSequenceRuleSetLoader.ruleThreshold(rules, "stage3-promoter-alignment-max-span", 2);
        int maxAge = OwnershipSequenceRuleSetLoader.ruleThreshold(rules, "stage3-ownership-sequence-max-age", 6);
        Attempt attempt = walkPairingSequence(historyAscending, maxSpan, maxAge, PROMOTER_HOLDING_INCREASE, ALIGNMENT_OTHER_CODES);
        return buildResult(instrumentId, symbol, OwnershipSequenceType.PROMOTER_INSTITUTION_ALIGNMENT, historyAscending, attempt, 2, maxSpan, List.of(PROMOTER_HOLDING_INCREASE, FII_ACCUMULATION, DII_ACCUMULATION, INSTITUTIONAL_OWNERSHIP_EXPANSION));
    }

    // ---- INSTITUTIONAL_OWNERSHIP_BUILDING: OR-first-step, orthogonal contradiction, alternate completion ----

    private Attempt walkOwnershipBuildingSequence(
        List<OwnershipInflectionHistoryEntry> history, int maxGapPeriods, int minPersistencePeriods,
        int contradictionTolerancePeriods, int maxAgePeriods
    ) {
        Attempt attempt = null;
        for (int i = 0; i < history.size(); i++) {
            OwnershipInflectionHistoryEntry entry = history.get(i);

            boolean terminal = attempt == null || attempt.phase() == OwnershipSequencePhase.COMPLETE || attempt.phase() == OwnershipSequencePhase.BROKEN;
            if (terminal) {
                if (entry.hasReason(FII_ACCUMULATION) || entry.hasReason(DII_ACCUMULATION)) {
                    attempt = Attempt.start(i, firstStepEvidence(entry));
                } else {
                    continue;
                }
            }

            // ---- progression pass ----
            if (attempt.currentStep() == 1) {
                if (entry.hasReason(INSTITUTIONAL_OWNERSHIP_EXPANSION)) {
                    attempt = attempt.advance(i, evidenceFor(INSTITUTIONAL_OWNERSHIP_EXPANSION, entry));
                    if (entry.hasReason(BULK_BUYING_WITH_OWNERSHIP_EXPANSION)) {
                        attempt = attempt.completeVia(true);
                    }
                } else if (!entry.hasReason(OWNERSHIP_CONTRADICTION)) {
                    // A contradicted period is not silence - it's actively information-bearing, just
                    // contradictory, and is governed entirely by its own separate tolerance mechanism
                    // below. Counting it toward the ordinary gap/age timer too would let a run of
                    // contradicted periods trip SEQUENCE_EXPIRED before the contradiction streak ever
                    // gets the chance to reach CONTRADICTORY_EVIDENCE.
                    int gap = i - attempt.lastStepIndex();
                    if (gap > maxGapPeriods || (i - attempt.firstIndex()) > maxAgePeriods) {
                        attempt = attempt.close(BrokenReason.EXPIRED);
                    }
                }
            } else if (attempt.currentStep() == 2 && attempt.phase() != OwnershipSequencePhase.COMPLETE) {
                if (entry.hasReason(INSTITUTIONAL_OWNERSHIP_EXPANSION)) {
                    attempt = attempt.touch(i, evidenceFor(INSTITUTIONAL_OWNERSHIP_EXPANSION, entry));
                    if (attempt.expansionPersistenceStreak() >= minPersistencePeriods) {
                        attempt = attempt.completeVia(false);
                    }
                }
                if (attempt.phase() != OwnershipSequencePhase.COMPLETE && entry.hasReason(BULK_BUYING_WITH_OWNERSHIP_EXPANSION)) {
                    attempt = attempt.completeVia(true);
                }
                if (attempt.phase() != OwnershipSequencePhase.COMPLETE && !entry.hasReason(INSTITUTIONAL_OWNERSHIP_EXPANSION) && !entry.hasReason(OWNERSHIP_CONTRADICTION)) {
                    attempt = attempt.withExpansionStreakReset();
                    if ((i - attempt.firstIndex()) > maxAgePeriods) {
                        attempt = attempt.close(BrokenReason.EXPIRED);
                    }
                }
            }

            // ---- contradiction pass (Correction 2: fully independent of the progression pass above) ----
            if (attempt.phase() != OwnershipSequencePhase.COMPLETE && attempt.phase() != OwnershipSequencePhase.BROKEN) {
                if (entry.hasReason(OWNERSHIP_CONTRADICTION)) {
                    attempt = attempt.withContradiction();
                    if (attempt.contradictionStreak() > contradictionTolerancePeriods) {
                        attempt = attempt.close(BrokenReason.CONTRADICTED);
                    }
                } else {
                    attempt = attempt.withContradictionReset();
                }
            }
        }
        return attempt;
    }

    // ---- BROAD_INSTITUTIONAL_PARTICIPATION / PROMOTER_INSTITUTION_ALIGNMENT: bounded pairing with persisted FORMING ----

    /**
     * Generalized prerequisite-pairing walk (mirrors {@code market.transformation}'s own
     * {@code stealthPrerequisitePairings}, generalized to accept side A's single code and side B's
     * candidate code set as parameters). Each side anchored to its own first occurrence since the
     * last reset; either order and same-period both valid. Unlike Market's Stage 3 (where pairing
     * silently produces no row until complete), one side pending persists a {@code FORMING} attempt
     * (Correction 4) - refreshed to track whichever anchor is currently the sole pending one if it
     * changes, and closed to {@code BROKEN} if both anchors lapse without pairing or the FORMING
     * attempt's own age exceeds {@code maxAgePeriods}.
     */
    private Attempt walkPairingSequence(
        List<OwnershipInflectionHistoryEntry> history, int maxSpanPeriods, int maxAgePeriods, String codeA, List<String> codesB
    ) {
        Attempt attempt = null;
        Integer pendingAIndex = null;
        Integer pendingBIndex = null;
        String pendingBCode = null;

        for (int i = 0; i < history.size(); i++) {
            OwnershipInflectionHistoryEntry entry = history.get(i);

            if (pendingAIndex != null && (i - pendingAIndex) > maxSpanPeriods) {
                pendingAIndex = null;
            }
            if (pendingBIndex != null && (i - pendingBIndex) > maxSpanPeriods) {
                pendingBIndex = null;
                pendingBCode = null;
            }
            if (entry.hasReason(codeA) && pendingAIndex == null) {
                pendingAIndex = i;
            }
            if (pendingBIndex == null) {
                for (String code : codesB) {
                    if (entry.hasReason(code)) {
                        pendingBIndex = i;
                        pendingBCode = code;
                        break;
                    }
                }
            }

            boolean terminal = attempt == null || attempt.phase() == OwnershipSequencePhase.COMPLETE || attempt.phase() == OwnershipSequencePhase.BROKEN;

            if (pendingAIndex != null && pendingBIndex != null) {
                int earlier = Math.min(pendingAIndex, pendingBIndex);
                int later = Math.max(pendingAIndex, pendingBIndex);
                boolean earlierIsA = earlier == pendingAIndex;
                String earlierCode = earlierIsA ? codeA : pendingBCode;
                String laterCode = earlierIsA ? pendingBCode : codeA;
                attempt = Attempt.start(earlier, evidenceFor(earlierCode, history.get(earlier)))
                                  .advance(later, evidenceFor(laterCode, history.get(later)))
                                  .completeVia(false);
                pendingAIndex = null;
                pendingBIndex = null;
                pendingBCode = null;
            } else if (pendingAIndex != null || pendingBIndex != null) {
                int soleIndex = pendingAIndex != null ? pendingAIndex : pendingBIndex;
                String soleCode = pendingAIndex != null ? codeA : pendingBCode;
                if (terminal || attempt.firstIndex() != soleIndex) {
                    attempt = Attempt.start(soleIndex, evidenceFor(soleCode, history.get(soleIndex)));
                }
            } else if (!terminal && attempt.phase() == OwnershipSequencePhase.FORMING) {
                attempt = attempt.close(BrokenReason.EXPIRED);
            }

            if (!terminal && attempt != null && attempt.phase() == OwnershipSequencePhase.FORMING
                && (i - attempt.firstIndex()) > maxAgePeriods) {
                attempt = attempt.close(BrokenReason.EXPIRED);
            }
        }
        return attempt;
    }

    // ---- result assembly ----

    private Optional<OwnershipSequenceResult> buildResult(
        UUID instrumentId, String symbol, OwnershipSequenceType sequenceType,
        List<OwnershipInflectionHistoryEntry> history, Attempt attempt, int totalSteps, int maxGapOrSpanPeriods,
        List<String> disambiguationCodes
    ) {
        if (attempt == null) {
            return Optional.empty();
        }

        LocalDate asOfDate = history.get(history.size() - 1).asOfDate();
        LocalDate firstStepDate = history.get(attempt.firstIndex()).asOfDate();
        LocalDate lastStepDate = history.get(attempt.lastStepIndex()).asOfDate();

        double confidence = weightedConfidence(attempt, totalSteps);
        boolean approachingExpiry = attempt.phase() != OwnershipSequencePhase.COMPLETE
            && (history.size() - 1 - attempt.lastStepIndex()) > maxGapOrSpanPeriods / 2.0;
        if (approachingExpiry) {
            confidence -= STALENESS_PENALTY;
        }
        confidence = clamp(confidence, 0.0, 100.0);

        double completionPct = (double) attempt.currentStep() / totalSteps * 100.0;
        double persistencePct = Math.min(100.0, attempt.stepPersistenceQuarters().get(attempt.stepPersistenceQuarters().size() - 1) / (double) PERSISTENCE_CAP * 100.0);
        double strength = clamp(0.80 * completionPct + 0.20 * persistencePct, 0.0, 100.0);

        boolean isBuildingSequence = sequenceType == OwnershipSequenceType.INSTITUTIONAL_OWNERSHIP_BUILDING;

        List<ReasonCode> reasons = new ArrayList<>();
        if (attempt.currentStep() >= 1) {
            reasons.add(ReasonCode.of("FIRST_STEP_DETECTED"));
        }
        if (attempt.currentStep() >= 2) {
            reasons.add(ReasonCode.of("SECOND_STEP_DETECTED"));
        }
        if (attempt.phase() == OwnershipSequencePhase.COMPLETE) {
            reasons.add(ReasonCode.of("ALL_REQUIRED_STEPS_COMPLETE"));
            if (isBuildingSequence) {
                reasons.add(attempt.completedViaBulkBuying() ? ReasonCode.of("BULK_BUYING_SUPPORT_PRESENT") : ReasonCode.of("PERSISTENCE_REQUIREMENT_MET"));
            }
        }
        if (attempt.phase() == OwnershipSequencePhase.BROKEN) {
            reasons.add(attempt.brokenReason() == BrokenReason.CONTRADICTED ? ReasonCode.of("CONTRADICTORY_EVIDENCE") : ReasonCode.of("SEQUENCE_EXPIRED"));
        }
        if (isBuildingSequence && attempt.phase() != OwnershipSequencePhase.BROKEN && attempt.contradictionStreak() > 0) {
            reasons.add(ReasonCode.of("PROMOTER_DILUTION_PRESENT"));
        }
        // Literal fired codes, for disambiguating which side of an OR/pairing actually satisfied a
        // step - required per docs/008 §14.2's own stated purpose ("differentiates FII-only from
        // FII+DII both moving"), re-derived directly from the real history rather than threaded
        // through Attempt.
        for (String code : disambiguationCodes) {
            if (history.get(attempt.firstIndex()).hasReason(code) || history.get(attempt.lastStepIndex()).hasReason(code)) {
                reasons.add(ReasonCode.of(code));
            }
        }

        return Optional.of(new OwnershipSequenceResult(
            instrumentId, symbol, asOfDate, sequenceType, attempt.phase(),
            attempt.currentStep(), totalSteps, firstStepDate, lastStepDate,
            strength, confidence, RULE_VERSION, reasons
        ));
    }

    private static double weightedConfidence(Attempt attempt, int totalSteps) {
        double[] fullWeights = {0.4, 0.6};
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

    // ---- reason code -> underlying Stage 1/2 evidence mapping ----

    private record StepEvidence(double confidence, int persistenceQuarters, boolean hasPrior) {
    }

    private static StepEvidence firstStepEvidence(OwnershipInflectionHistoryEntry entry) {
        boolean fii = entry.hasReason(FII_ACCUMULATION);
        boolean dii = entry.hasReason(DII_ACCUMULATION);
        if (fii && dii) {
            return jointEvidence(fromObservation(entry.fii()), fromObservation(entry.dii()));
        }
        return fii ? fromObservation(entry.fii()) : fromObservation(entry.dii());
    }

    private static StepEvidence evidenceFor(String reasonCode, OwnershipInflectionHistoryEntry entry) {
        return switch (reasonCode) {
            case PROMOTER_HOLDING_INCREASE -> fromObservation(entry.promoter());
            case FII_ACCUMULATION -> fromObservation(entry.fii());
            case DII_ACCUMULATION -> fromObservation(entry.dii());
            case INSTITUTIONAL_OWNERSHIP_EXPANSION, BULK_BUYING_WITH_OWNERSHIP_EXPANSION -> fromDrivingMetric(entry);
            default -> throw new IllegalArgumentException("Unknown reason code: " + reasonCode);
        };
    }

    /**
     * {@code INSTITUTIONAL_OWNERSHIP_EXPANSION}/{@code BULK_BUYING_WITH_OWNERSHIP_EXPANSION} are
     * composite states - resolve evidence from the Stage 2 row's own stored {@code driving_metric}
     * (Correction 3), never an unconditional {@code min(FII, DII)}, which can understate confidence
     * or manufacture missing evidence when only one institutional category actually drove the state.
     * Verified against {@code OwnershipTransformationEngine.drivingMetricFor}: whenever
     * {@code INSTITUTIONAL_OWNERSHIP_EXPANSION} fires, both {@code fiiUp}/{@code diiUp} are
     * guaranteed true, so {@code driving_metric} is always deterministically FII or DII, never
     * ambiguous, regardless of which primary_state ultimately won that row's priority ladder.
     */
    private static StepEvidence fromDrivingMetric(OwnershipInflectionHistoryEntry entry) {
        if (entry.drivingMetric() == TransformationMetric.FII) {
            return fromObservation(entry.fii());
        }
        if (entry.drivingMetric() == TransformationMetric.DII) {
            return fromObservation(entry.dii());
        }
        // Defensive fallback only - should never trigger against real data (see javadoc above).
        return jointEvidence(fromObservation(entry.fii()), fromObservation(entry.dii()));
    }

    private static StepEvidence fromObservation(OwnershipEvidenceObservation observation) {
        if (observation == null) {
            // Defensive only - a reason code firing structurally implies its underlying exact-quarter
            // evidence exists (OwnershipInflectionHistoryReader's exact-period_end join, Correction 3);
            // this should never actually happen against real data.
            return new StepEvidence(0.0, 0, false);
        }
        return new StepEvidence(observation.confidence(), observation.persistenceQuarters(), observation.priorPeriodEnd() != null);
    }

    private static StepEvidence jointEvidence(StepEvidence a, StepEvidence b) {
        return new StepEvidence(Math.min(a.confidence(), b.confidence()), Math.min(a.persistenceQuarters(), b.persistenceQuarters()), a.hasPrior() && b.hasPrior());
    }

    // ---- immutable attempt state ----

    private enum BrokenReason {
        EXPIRED, CONTRADICTED
    }

    private record Attempt(
        int currentStep, int firstIndex, int lastStepIndex, OwnershipSequencePhase phase,
        List<Double> stepConfidences, List<Integer> stepPersistenceQuarters, List<Boolean> stepHasPrior,
        int contradictionStreak, int expansionPersistenceStreak, boolean completedViaBulkBuying, BrokenReason brokenReason
    ) {
        static Attempt start(int index, StepEvidence evidence) {
            return new Attempt(1, index, index, OwnershipSequencePhase.FORMING,
                List.of(evidence.confidence()), List.of(evidence.persistenceQuarters()), List.of(evidence.hasPrior()),
                0, 0, false, null);
        }

        /**
         * Normal step progression to step 2 - always {@code PROGRESSING}, never auto-{@code COMPLETE}
         * (unlike Market's own {@code advance}, reaching the last step is not itself completion for
         * either Ownership sequence family: the building sequence needs a separate persistence/
         * bulk-buying criterion, the pairing sequences call {@link #completeVia} explicitly right
         * after advancing). {@code contradictionStreak} passes through UNCHANGED (Correction 2 -
         * never reset here, only by the dedicated contradiction pass).
         */
        Attempt advance(int index, StepEvidence evidence) {
            return new Attempt(currentStep + 1, firstIndex, index, OwnershipSequencePhase.PROGRESSING,
                append(stepConfidences, evidence.confidence()), append(stepPersistenceQuarters, evidence.persistenceQuarters()), append(stepHasPrior, evidence.hasPrior()),
                contradictionStreak, 1, completedViaBulkBuying, brokenReason);
        }

        /** Step 2 (EXPANSION) recurs in a later period without advancing currentStep further - extends the persistence streak and refreshes the last-step evidence. */
        Attempt touch(int index, StepEvidence evidence) {
            return new Attempt(currentStep, firstIndex, index, phase,
                replaceLast(stepConfidences, evidence.confidence()), replaceLast(stepPersistenceQuarters, evidence.persistenceQuarters()), replaceLast(stepHasPrior, evidence.hasPrior()),
                contradictionStreak, expansionPersistenceStreak + 1, completedViaBulkBuying, brokenReason);
        }

        /** A quiet (non-EXPANSION) period at step 2 - resets the persistence run, since it's no longer genuinely consecutive. */
        Attempt withExpansionStreakReset() {
            return expansionPersistenceStreak == 0 ? this
                : new Attempt(currentStep, firstIndex, lastStepIndex, phase, stepConfidences, stepPersistenceQuarters, stepHasPrior,
                    contradictionStreak, 0, completedViaBulkBuying, brokenReason);
        }

        /** Soft-touch on OWNERSHIP_CONTRADICTION - increments the streak only; caller decides whether to close based on tolerance. */
        Attempt withContradiction() {
            return new Attempt(currentStep, firstIndex, lastStepIndex, phase, stepConfidences, stepPersistenceQuarters, stepHasPrior,
                contradictionStreak + 1, expansionPersistenceStreak, completedViaBulkBuying, brokenReason);
        }

        /** A clean (non-contradicted) period - resets the streak, since it's consecutive-contradiction only. */
        Attempt withContradictionReset() {
            return contradictionStreak == 0 ? this
                : new Attempt(currentStep, firstIndex, lastStepIndex, phase, stepConfidences, stepPersistenceQuarters, stepHasPrior,
                    0, expansionPersistenceStreak, completedViaBulkBuying, brokenReason);
        }

        /** Alternate/accelerated completion, whichever path (persistence or bulk-buying) reaches it first. */
        Attempt completeVia(boolean viaBulkBuying) {
            return new Attempt(currentStep, firstIndex, lastStepIndex, OwnershipSequencePhase.COMPLETE, stepConfidences, stepPersistenceQuarters, stepHasPrior,
                contradictionStreak, expansionPersistenceStreak, viaBulkBuying, brokenReason);
        }

        Attempt close(BrokenReason reason) {
            return new Attempt(currentStep, firstIndex, lastStepIndex, OwnershipSequencePhase.BROKEN, stepConfidences, stepPersistenceQuarters, stepHasPrior,
                contradictionStreak, expansionPersistenceStreak, completedViaBulkBuying, reason);
        }

        private static <T> List<T> append(List<T> list, T value) {
            List<T> copy = new ArrayList<>(list);
            copy.add(value);
            return List.copyOf(copy);
        }

        private static <T> List<T> replaceLast(List<T> list, T value) {
            List<T> copy = new ArrayList<>(list.subList(0, list.size() - 1));
            copy.add(value);
            return List.copyOf(copy);
        }
    }
}
