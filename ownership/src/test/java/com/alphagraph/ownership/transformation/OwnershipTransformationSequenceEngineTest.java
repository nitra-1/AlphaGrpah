package com.alphagraph.ownership.transformation;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class OwnershipTransformationSequenceEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "COALINDIA";
    private static final LocalDate Q0 = LocalDate.of(2024, 6, 30);
    private static final RuleSet DEFAULT_RULES = new RuleSet(1, List.of());

    private final OwnershipTransformationSequenceEngine engine = new OwnershipTransformationSequenceEngine();

    // ---- INSTITUTIONAL_OWNERSHIP_BUILDING: docs/008 §21 named cases ----

    @Test
    void fiiOnlyIsForming() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null)
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.FORMING);
        assertThat(result.get().currentStep()).isEqualTo(1);
        assertThat(result.get().reasons()).extracting("code").contains("FII_ACCUMULATION");
    }

    @Test
    void fiiThenExpansionReachesProgressingWhenPersistenceNotYetMet() {
        // default stage3-ownership-building-min-persistence is 2 - a single EXPANSION period isn't enough.
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            expansionEntry(1, 75.0, 1, true, TransformationMetric.FII)
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.PROGRESSING);
        assertThat(result.get().currentStep()).isEqualTo(2);
    }

    @Test
    void expansionPersistingTwoConsecutiveQuartersCompletesViaPersistence() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            expansionEntry(1, 75.0, 1, true, TransformationMetric.FII),
            expansionEntry(2, 75.0, 2, true, TransformationMetric.FII)
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.COMPLETE);
        assertThat(result.get().reasons()).extracting("code").contains("ALL_REQUIRED_STEPS_COMPLETE", "PERSISTENCE_REQUIREMENT_MET");
        assertThat(result.get().reasons()).extracting("code").doesNotContain("BULK_BUYING_SUPPORT_PRESENT");
    }

    @Test
    void bulkBuyingCompletesImmediatelyBeforePersistenceIsMet() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            entry(1, Set.of("INSTITUTIONAL_OWNERSHIP_EXPANSION", "BULK_BUYING_WITH_OWNERSHIP_EXPANSION"), null, fii(1, 75.0, 1, true), dii(1, 75.0, 1, true), TransformationMetric.FII)
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.COMPLETE);
        assertThat(result.get().reasons()).extracting("code").contains("BULK_BUYING_SUPPORT_PRESENT");
        assertThat(result.get().reasons()).extracting("code").doesNotContain("PERSISTENCE_REQUIREMENT_MET");
    }

    @Test
    void bulkBuyingArrivingAfterPersistenceCompletionDoesNotDoubleComplete() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            expansionEntry(1, 75.0, 1, true, TransformationMetric.FII),
            expansionEntry(2, 75.0, 2, true, TransformationMetric.FII), // completes here via persistence
            entry(3, Set.of("BULK_BUYING_WITH_OWNERSHIP_EXPANSION"), null, fii(3, 75.0, 3, true), null, TransformationMetric.FII)
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        // still the ORIGINAL completion (quarter index 2), never re-triggered/extended by the later bulk-buying period
        assertThat(result.get().lastStepDate()).isEqualTo(Q0.plusMonths(6));
        assertThat(result.get().reasons()).extracting("code").contains("PERSISTENCE_REQUIREMENT_MET");
    }

    @Test
    void oneDilutionDoesNotBreakTheSequence() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            entry(1, Set.of("OWNERSHIP_CONTRADICTION"), null, fii(1, 75.0, 1, true), null), // one contradicted period, no progression
            expansionEntry(2, 75.0, 1, true, TransformationMetric.FII) // then a clean period with real progress
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isNotEqualTo(OwnershipSequencePhase.BROKEN);
        // the contradiction streak reset on the clean period that followed - no lingering pressure reason
        assertThat(result.get().reasons()).extracting("code").doesNotContain("PROMOTER_DILUTION_PRESENT");
    }

    @Test
    void persistentContradictionBreaksTheSequence() {
        // default stage3-ownership-contradiction-tolerance is 2 - 3 consecutive contradicted quarters must break it.
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            entry(1, Set.of("OWNERSHIP_CONTRADICTION"), null, fii(1, 75.0, 1, true), null),
            entry(2, Set.of("OWNERSHIP_CONTRADICTION"), null, fii(2, 75.0, 1, true), null),
            entry(3, Set.of("OWNERSHIP_CONTRADICTION"), null, fii(3, 75.0, 1, true), null)
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.BROKEN);
        assertThat(result.get().reasons()).extracting("code").contains("CONTRADICTORY_EVIDENCE");
        assertThat(result.get().reasons()).extracting("code").doesNotContain("SEQUENCE_EXPIRED");
    }

    @Test
    void contradictionStreakExactlyAtToleranceSurvives() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            entry(1, Set.of("OWNERSHIP_CONTRADICTION"), null, fii(1, 75.0, 1, true), null),
            entry(2, Set.of("OWNERSHIP_CONTRADICTION"), null, fii(2, 75.0, 1, true), null) // streak == tolerance (2) - must not break
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isNotEqualTo(OwnershipSequencePhase.BROKEN);
        assertThat(result.get().reasons()).extracting("code").contains("PROMOTER_DILUTION_PRESENT");
    }

    // ---- Correction 2: a single quarter can both advance AND accrue contradiction pressure ----

    @Test
    void expansionAndContradictionInTheSameQuarterBothProgressAndIncrementTheStreak() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            // this single quarter carries BOTH EXPANSION and CONTRADICTION reasons at once - real
            // per OwnershipTransformationEngine.bandStates() when fiiUp && diiUp && promoterDown
            entry(1, Set.of("INSTITUTIONAL_OWNERSHIP_EXPANSION", "OWNERSHIP_CONTRADICTION"), null, fii(1, 75.0, 1, true), dii(1, 75.0, 1, true), TransformationMetric.FII)
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        // progression happened (step 2 reached) even though the same period was also contradicted
        assertThat(result.get().currentStep()).isEqualTo(2);
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.PROGRESSING);
        // and contradiction pressure is genuinely registered, not silently reset by the progression
        assertThat(result.get().reasons()).extracting("code").contains("PROMOTER_DILUTION_PRESENT");
    }

    @Test
    void gapExceedingMaxBreaksTheSequence() {
        // default stage3-ownership-building-max-gap is 2 - 3 silent quarters after FORMING must expire it.
        List<OwnershipInflectionHistoryEntry> history = new ArrayList<>();
        history.add(entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null));
        for (int i = 1; i <= 3; i++) {
            history.add(entry(i, Set.of(), null, null, null));
        }

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.BROKEN);
        assertThat(result.get().reasons()).extracting("code").contains("SEQUENCE_EXPIRED");
    }

    @Test
    void overallMaxAgeExpiresAnIncompleteSequenceEvenWithoutASingleGapViolation() {
        RuleSet rules = ruleSet("stage3-ownership-building-max-gap", 100, "stage3-ownership-sequence-max-age", 2);
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            entry(1, Set.of(), null, null, null),
            entry(2, Set.of(), null, null, null),
            entry(3, Set.of(), null, null, null)
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, rules);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.BROKEN);
    }

    @Test
    void neverFiringProducesNoResultAtAll() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of(), null, null, null),
            entry(1, Set.of(), null, null, null)
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isEmpty();
    }

    @Test
    void expansionAloneNeverStartsTheSequenceWithoutFiiOrDiiFirst() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("INSTITUTIONAL_OWNERSHIP_EXPANSION"), null, fii(0, 90.0, 1, true), dii(0, 90.0, 1, true), TransformationMetric.FII)
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        // wrong-order data (Stage 2 doesn't enforce order) - never a valid start
        assertThat(result).isEmpty();
    }

    @Test
    void bothFiiAndDiiInTheSameStartingQuarterProduceOneAttemptNotTwo() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION", "DII_ACCUMULATION"), null, fii(0, 90.0, 1, true), dii(0, 60.0, 1, true), null)
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().currentStep()).isEqualTo(1);
        assertThat(result.get().reasons()).extracting("code").contains("FII_ACCUMULATION", "DII_ACCUMULATION");
    }

    // ---- Correction 3: composite-state evidence resolves from Stage 2's own driving_metric ----

    @Test
    void fiiOnlyDrivingMetricUsesFiiEvidenceWithoutRequiringDii() {
        // DII observation is entirely absent this quarter (thin/pending XBRL) - driving_metric=FII
        // must still resolve real evidence, never an unconditional min(FII, DII) that would see a
        // missing DII and manufacture a confidence of 0.
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            entry(1, Set.of("INSTITUTIONAL_OWNERSHIP_EXPANSION"), null, fii(1, 82.0, 2, true), null, TransformationMetric.FII)
        );

        var result = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        // 2-step weights 40/60: step1 (FII, conf=90) * 0.4 + step2 (driving_metric=FII, conf=82) * 0.6
        double expected = 90.0 * 0.4 + 82.0 * 0.6;
        assertThat(result.get().confidence()).isCloseTo(expected, offset(0.01));
    }

    // ---- BROAD_INSTITUTIONAL_PARTICIPATION ----

    @Test
    void oneSideAloneProducesAPersistedFormingRow_notSilence() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null)
        );

        var result = engine.evaluateBroadInstitutionalParticipation(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.FORMING);
        assertThat(result.get().currentStep()).isEqualTo(1);
    }

    @Test
    void fiiAndDiiPairingWithinWindowCompletesAndRecordsBothCodes() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            entry(1, Set.of("DII_ACCUMULATION"), null, null, dii(1, 80.0, 1, true))
        );

        var result = engine.evaluateBroadInstitutionalParticipation(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.COMPLETE);
        assertThat(result.get().reasons()).extracting("code").contains("FII_ACCUMULATION", "DII_ACCUMULATION");
    }

    @Test
    void pairingOutsideTheWindowNeverCompletes_theStaleAnchorExpires() {
        // default stage3-broad-participation-max-span is 2
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            entry(1, Set.of(), null, null, null),
            entry(2, Set.of(), null, null, null),
            entry(3, Set.of(), null, null, null),
            entry(4, Set.of("DII_ACCUMULATION"), null, null, dii(4, 80.0, 1, true)) // 4 quarters later, past the window
        );

        var result = engine.evaluateBroadInstitutionalParticipation(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        // the FII anchor already expired (gap + age) before DII ever fired - a fresh, later FORMING attempt on DII alone, never COMPLETE
        assertThat(result.get().sequencePhase()).isNotEqualTo(OwnershipSequencePhase.COMPLETE);
    }

    // ---- PROMOTER_INSTITUTION_ALIGNMENT ----

    @Test
    void promoterAloneProducesAPersistedFormingRow() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("PROMOTER_HOLDING_INCREASE"), promoter(0, 90.0, 1, true), null, null)
        );

        var result = engine.evaluatePromoterInstitutionAlignment(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.FORMING);
    }

    @Test
    void promoterAndFiiAlignmentCompletesAndRecordsTheSatisfyingCode() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("PROMOTER_HOLDING_INCREASE"), promoter(0, 90.0, 1, true), null, null),
            entry(1, Set.of("FII_ACCUMULATION"), null, fii(1, 80.0, 1, true), null)
        );

        var result = engine.evaluatePromoterInstitutionAlignment(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.COMPLETE);
        assertThat(result.get().reasons()).extracting("code").contains("PROMOTER_HOLDING_INCREASE", "FII_ACCUMULATION");
    }

    @Test
    void promoterAndDiiAlignmentCompletesAndRecordsTheSatisfyingCode() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("PROMOTER_HOLDING_INCREASE"), promoter(0, 90.0, 1, true), null, null),
            entry(1, Set.of("DII_ACCUMULATION"), null, null, dii(1, 80.0, 1, true))
        );

        var result = engine.evaluatePromoterInstitutionAlignment(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.COMPLETE);
        assertThat(result.get().reasons()).extracting("code").contains("PROMOTER_HOLDING_INCREASE", "DII_ACCUMULATION");
    }

    @Test
    void promoterAndExpansionAlignmentCompletesAndRecordsTheSatisfyingCode() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("PROMOTER_HOLDING_INCREASE"), promoter(0, 90.0, 1, true), null, null),
            entry(1, Set.of("INSTITUTIONAL_OWNERSHIP_EXPANSION"), null, fii(1, 75.0, 1, true), dii(1, 75.0, 1, true), TransformationMetric.DII)
        );

        var result = engine.evaluatePromoterInstitutionAlignment(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.COMPLETE);
        assertThat(result.get().reasons()).extracting("code").contains("PROMOTER_HOLDING_INCREASE", "INSTITUTIONAL_OWNERSHIP_EXPANSION");
    }

    // ---- readiness: independent of whether any sequence fired ----

    @Test
    void readinessIsInsufficientHistoryBelowThreePeriodsRegardlessOfSequenceActivity() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            entry(1, Set.of(), null, null, null)
        );

        var readiness = engine.evaluateReadiness(INSTRUMENT_ID, SYMBOL, Q0.plusMonths(3), history);

        assertThat(readiness.readiness()).isEqualTo(OwnershipSequenceReadiness.INSUFFICIENT_HISTORY);
        assertThat(readiness.historyPeriods()).isEqualTo(2);
    }

    @Test
    void readinessIsReadyAtThreeOrMorePeriodsEvenWhenNoSequenceEverFires() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of(), null, null, null), entry(1, Set.of(), null, null, null), entry(2, Set.of(), null, null, null)
        );

        var readiness = engine.evaluateReadiness(INSTRUMENT_ID, SYMBOL, Q0.plusMonths(6), history);
        var sequence = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(readiness.readiness()).isEqualTo(OwnershipSequenceReadiness.READY);
        assertThat(sequence).isEmpty();
    }

    // ---- same-history rerun: pure function, identical input must give identical output (idempotency) ----

    @Test
    void sameHistoryEvaluatedTwiceProducesIdenticalResults() {
        List<OwnershipInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null)
        );

        var first = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);
        var second = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(first).isEqualTo(second);
    }

    // ---- backfill point-in-time correctness: a later quarter must never leak into an earlier bound ----

    @Test
    void aTruncatedHistoryNeverSeesLaterEvidence() {
        List<OwnershipInflectionHistoryEntry> full = List.of(
            entry(0, Set.of("FII_ACCUMULATION"), null, fii(0, 90.0, 1, true), null),
            expansionEntry(1, 75.0, 1, true, TransformationMetric.FII),
            expansionEntry(2, 75.0, 2, true, TransformationMetric.FII) // completes via persistence here
        );
        List<OwnershipInflectionHistoryEntry> truncatedAtStep1 = full.subList(0, 1);

        var fullResult = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, full, DEFAULT_RULES);
        var truncatedResult = engine.evaluateInstitutionalOwnershipBuilding(INSTRUMENT_ID, SYMBOL, truncatedAtStep1, DEFAULT_RULES);

        assertThat(fullResult.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.COMPLETE);
        assertThat(truncatedResult.get().sequencePhase()).isEqualTo(OwnershipSequencePhase.FORMING);
        assertThat(truncatedResult.get().currentStep()).isEqualTo(1);
    }

    // ---- helpers ----

    private static OwnershipInflectionHistoryEntry entry(
        int quarterOffset, Set<String> reasons, OwnershipEvidenceObservation promoter, OwnershipEvidenceObservation fii, OwnershipEvidenceObservation dii
    ) {
        return entry(quarterOffset, reasons, promoter, fii, dii, null);
    }

    private static OwnershipInflectionHistoryEntry entry(
        int quarterOffset, Set<String> reasons, OwnershipEvidenceObservation promoter, OwnershipEvidenceObservation fii, OwnershipEvidenceObservation dii,
        TransformationMetric drivingMetric
    ) {
        LocalDate periodEnd = Q0.plusMonths(3L * quarterOffset);
        return new OwnershipInflectionHistoryEntry(periodEnd, periodEnd, SYMBOL, reasons, drivingMetric, promoter, fii, dii);
    }

    private static OwnershipInflectionHistoryEntry expansionEntry(int quarterOffset, double confidence, int persistenceQuarters, boolean hasPrior, TransformationMetric drivingMetric) {
        return entry(
            quarterOffset, Set.of("INSTITUTIONAL_OWNERSHIP_EXPANSION"), null,
            drivingMetric == TransformationMetric.FII ? fii(quarterOffset, confidence, persistenceQuarters, hasPrior) : fii(quarterOffset, 50.0, 0, false),
            drivingMetric == TransformationMetric.DII ? dii(quarterOffset, confidence, persistenceQuarters, hasPrior) : dii(quarterOffset, 50.0, 0, false),
            drivingMetric
        );
    }

    private static OwnershipEvidenceObservation promoter(int quarterOffset, double confidence, int persistenceQuarters, boolean hasPrior) {
        return observation(TransformationMetric.PROMOTER, quarterOffset, confidence, persistenceQuarters, hasPrior);
    }

    private static OwnershipEvidenceObservation fii(int quarterOffset, double confidence, int persistenceQuarters, boolean hasPrior) {
        return observation(TransformationMetric.FII, quarterOffset, confidence, persistenceQuarters, hasPrior);
    }

    private static OwnershipEvidenceObservation dii(int quarterOffset, double confidence, int persistenceQuarters, boolean hasPrior) {
        return observation(TransformationMetric.DII, quarterOffset, confidence, persistenceQuarters, hasPrior);
    }

    private static OwnershipEvidenceObservation observation(TransformationMetric metric, int quarterOffset, double confidence, int persistenceQuarters, boolean hasPrior) {
        LocalDate periodEnd = Q0.plusMonths(3L * quarterOffset);
        return new OwnershipEvidenceObservation(
            metric, INSTRUMENT_ID, SYMBOL, periodEnd, hasPrior ? periodEnd.minusMonths(3) : null,
            BigDecimal.ONE, hasPrior ? BigDecimal.ZERO : null, BigDecimal.ONE, BigDecimal.ONE, persistenceQuarters, confidence
        );
    }

    private static RuleSet ruleSet(String name1, int value1, String name2, int value2) {
        return new RuleSet(1, List.of(rule(name1, value1), rule(name2, value2)));
    }

    private static Rule rule(String name, int threshold) {
        return new Rule(name, "n/a", 1, List.of(new RuleCondition(RuleOperator.ALWAYS, threshold, threshold)));
    }
}
