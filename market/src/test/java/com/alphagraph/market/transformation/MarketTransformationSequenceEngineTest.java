package com.alphagraph.market.transformation;

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

class MarketTransformationSequenceEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate D0 = LocalDate.of(2026, 8, 1);
    private static final RuleSet DEFAULT_RULES = new RuleSet(1, List.of());

    private final MarketTransformationSequenceEngine engine = new MarketTransformationSequenceEngine();

    // ---- DELIVERY_LED_ACCUMULATION ----

    @Test
    void deliveryOnlyIsForming() {
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("DELIVERY_RISING"), delivery(0, 90.0, 1, true), null, null)
        );

        var result = engine.evaluateDeliveryLedAccumulation(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(MarketSequencePhase.FORMING);
        assertThat(result.get().currentStep()).isEqualTo(1);
        assertThat(result.get().totalSteps()).isEqualTo(2);
    }

    @Test
    void sustainedReasonCompletesDeliveryLedAccumulation() {
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("DELIVERY_RISING"), delivery(0, 90.0, 1, true), null, null),
            entry(1, Set.of("DELIVERY_RISING", "DELIVERY_EXPANSION_SUSTAINED_5D"), delivery(1, 90.0, 5, true), null, null)
        );

        var result = engine.evaluateDeliveryLedAccumulation(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(MarketSequencePhase.COMPLETE);
        assertThat(result.get().currentStep()).isEqualTo(2);
        assertThat(result.get().firstStepDate()).isEqualTo(D0);
        assertThat(result.get().lastStepDate()).isEqualTo(D0.plusDays(1));
    }

    @Test
    void gapExceedingMaxBreaksTheSequence() {
        // default stage3-delivery-led-accumulation-max-gap is 3 - 4 silent sessions after FORMING must expire it.
        List<MarketInflectionHistoryEntry> history = new ArrayList<>();
        history.add(entry(0, Set.of("DELIVERY_RISING"), delivery(0, 90.0, 1, true), null, null));
        for (int i = 1; i <= 4; i++) {
            history.add(entry(i, Set.of(), null, null, null));
        }

        var result = engine.evaluateDeliveryLedAccumulation(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(MarketSequencePhase.BROKEN);
        assertThat(result.get().reasons()).extracting("code").contains("SEQUENCE_EXPIRED");
    }

    @Test
    void overallMaxAgeExpiresAnIncompleteSequenceEvenWithoutASingleGapViolation() {
        // A tiny custom max-age (2) with a generous per-step gap (100) - the age cap must still fire.
        RuleSet rules = ruleSet("stage3-delivery-led-accumulation-max-gap", 100, "stage3-sequence-max-age", 2);
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("DELIVERY_RISING"), delivery(0, 90.0, 1, true), null, null),
            entry(1, Set.of(), null, null, null),
            entry(2, Set.of(), null, null, null),
            entry(3, Set.of(), null, null, null)
        );

        var result = engine.evaluateDeliveryLedAccumulation(INSTRUMENT_ID, SYMBOL, history, rules);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(MarketSequencePhase.BROKEN);
    }

    @Test
    void neverFiringProducesNoResultAtAll() {
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of(), null, null, null),
            entry(1, Set.of(), null, null, null)
        );

        var result = engine.evaluateDeliveryLedAccumulation(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isEmpty();
    }

    // ---- Correction 1: explicit attempt lifecycle, zero carryover into a fresh attempt ----

    @Test
    void aFreshAttemptAfterCompleteCarriesNoneOfTheOldCyclesEvidence() {
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("DELIVERY_RISING"), delivery(0, 60.0, 1, true), null, null),
            entry(1, Set.of("DELIVERY_RISING", "DELIVERY_EXPANSION_SUSTAINED_5D"), delivery(1, 60.0, 5, true), null, null), // first cycle completes here
            entry(2, Set.of(), null, null, null),
            entry(3, Set.of(), null, null, null),
            entry(4, Set.of(), null, null, null),
            entry(5, Set.of(), null, null, null), // gap > 3 since last step - but attempt is already COMPLETE, not FORMING, so this doesn't matter
            entry(6, Set.of("DELIVERY_RISING"), delivery(6, 95.0, 1, true), null, null) // a brand-new attempt starts here
        );

        var result = engine.evaluateDeliveryLedAccumulation(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(MarketSequencePhase.FORMING);
        assertThat(result.get().firstStepDate()).isEqualTo(D0.plusDays(6)); // the new attempt's own date, never the old cycle's D0
        assertThat(result.get().confidence()).isCloseTo(95.0, offset(0.01)); // the new attempt's own confidence, never the old cycle's 60.0
    }

    // ---- Correction 2: Stealth's two prerequisites need a bounded, resettable pairing window ----

    @Test
    void stealthPrerequisitesPairWithinTheConfiguredWindow() {
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("DELIVERY_RISING"), delivery(0, 90.0, 1, true), null, null),
            entry(10, Set.of("RELATIVE_VOLUME_RISING"), null, relativeVolume(10, 90.0, 1, true), null) // 10 sessions later, within default window of 20
        );

        var result = engine.evaluateStealthAccumulationSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(MarketSequencePhase.FORMING);
        assertThat(result.get().currentStep()).isEqualTo(1);
    }

    @Test
    void stealthPrerequisitesDoNotPairAcrossAnExpiredWindow_missingEvidenceIsNeverStaleEvidence() {
        List<MarketInflectionHistoryEntry> history = new ArrayList<>();
        history.add(entry(0, Set.of("DELIVERY_RISING"), delivery(0, 90.0, 1, true), null, null));
        for (int i = 1; i < 21; i++) {
            history.add(entry(i, Set.of(), null, null, null));
        }
        history.add(entry(21, Set.of("RELATIVE_VOLUME_RISING"), null, relativeVolume(21, 90.0, 1, true), null)); // 21 sessions later, past the default 20-session window

        var result = engine.evaluateStealthAccumulationSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isEmpty(); // the old DELIVERY_RISING must not wrongly pair with today's RELATIVE_VOLUME_RISING
    }

    @Test
    void stealthPrerequisitesPairOnTheSameSessionRegardlessOfOrder() {
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("DELIVERY_RISING", "RELATIVE_VOLUME_RISING"), delivery(0, 90.0, 1, true), relativeVolume(0, 80.0, 1, true), null)
        );

        var result = engine.evaluateStealthAccumulationSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().currentStep()).isEqualTo(1);
    }

    @Test
    void stealthCandidateReasonCompletesTheSequenceAfterPairing() {
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("RELATIVE_VOLUME_RISING"), null, relativeVolume(0, 90.0, 1, true), null), // volume first
            entry(3, Set.of("DELIVERY_RISING"), delivery(3, 90.0, 1, true), null, null), // then delivery, within window - either order valid
            entry(5, Set.of("VOLUME_DELIVERY_UP_PRICE_FLAT"), delivery(5, 90.0, 3, true), relativeVolume(5, 85.0, 2, true), priceReturn(5, 88.0, 1, true))
        );

        var result = engine.evaluateStealthAccumulationSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(MarketSequencePhase.COMPLETE);
    }

    // ---- MARKET_RECOGNITION_SEQUENCE - the reference sequence ----

    @Test
    void marketRecognitionSequenceCompletesInTheCorrectOrder() {
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("DELIVERY_EXPANSION_SUSTAINED_5D", "DELIVERY_RISING"), delivery(0, 90.0, 5, true), null, null),
            entry(4, Set.of("VOLUME_DELIVERY_UP_PRICE_FLAT"), delivery(4, 90.0, 6, true), relativeVolume(4, 85.0, 2, true), priceReturn(4, 88.0, 1, true)),
            entry(9, Set.of("BREAKOUT_FROM_STEALTH_ACCUMULATION"), null, null, priceReturn(9, 92.0, 0, true))
        );

        var result = engine.evaluateMarketRecognitionSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(MarketSequencePhase.COMPLETE);
        assertThat(result.get().currentStep()).isEqualTo(3);
        assertThat(result.get().firstStepDate()).isEqualTo(D0);
        assertThat(result.get().lastStepDate()).isEqualTo(D0.plusDays(9));
    }

    @Test
    void marketRecognitionSequenceNeverStartsWithoutSustainedDeliveryFiringFirst() {
        // STEALTH_CANDIDATE and BREAKOUT appear, but SUSTAINED never does - Stage 2 doesn't enforce
        // this order itself; Stage 3 must.
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("VOLUME_DELIVERY_UP_PRICE_FLAT"), delivery(0, 90.0, 2, true), relativeVolume(0, 85.0, 2, true), priceReturn(0, 88.0, 1, true)),
            entry(3, Set.of("BREAKOUT_FROM_STEALTH_ACCUMULATION"), null, null, priceReturn(3, 92.0, 0, true))
        );

        var result = engine.evaluateMarketRecognitionSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isEmpty();
    }

    // ---- Correction 3: step confidence/persistence come from the reason's own metric, never a blended/wrong one ----

    @Test
    void stepConfidenceIsSourcedFromTheReasonsOwnMetric_notAnyOtherMetricPresentThatDay() {
        // Delivery's own confidence (70) is deliberately different from relative-volume's (95) on
        // the same day - DELIVERY_RISING's step evidence must use delivery's 70, never volume's 95.
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("DELIVERY_RISING"), delivery(0, 70.0, 1, true), relativeVolume(0, 95.0, 1, true), null)
        );

        var result = engine.evaluateDeliveryLedAccumulation(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isCloseTo(70.0, offset(0.01));
    }

    @Test
    void stealthCandidateConfidenceIsTheMinimumAcrossAllThreeContributingMetrics() {
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("DELIVERY_RISING"), delivery(0, 90.0, 1, true), null, null),
            entry(1, Set.of("RELATIVE_VOLUME_RISING"), null, relativeVolume(1, 90.0, 1, true), null),
            entry(2, Set.of("VOLUME_DELIVERY_UP_PRICE_FLAT"), delivery(2, 90.0, 3, true), relativeVolume(2, 40.0, 2, true), priceReturn(2, 85.0, 1, true))
        );

        var result = engine.evaluateStealthAccumulationSequence(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        // 2-step weights 40/60; step1 (pairing) confidence = min(90,90)=90, step2 (candidate) = min(90,40,85)=40
        double expected = 90.0 * 0.4 + 40.0 * 0.6;
        assertThat(result.get().confidence()).isCloseTo(expected, offset(0.01));
    }

    // ---- Correction 4: readiness is independent of whether any sequence fired ----

    @Test
    void readinessIsInsufficientHistoryBelowFiveSessionsRegardlessOfSequenceActivity() {
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("DELIVERY_RISING"), delivery(0, 90.0, 1, true), null, null),
            entry(1, Set.of(), null, null, null)
        );

        var readiness = engine.evaluateReadiness(INSTRUMENT_ID, SYMBOL, D0.plusDays(1), history);

        assertThat(readiness.readiness()).isEqualTo(MarketSequenceReadiness.INSUFFICIENT_HISTORY);
        assertThat(readiness.historySessions()).isEqualTo(2);
    }

    @Test
    void readinessIsReadyAtFiveOrMoreSessionsEvenWhenNoSequenceEverFires() {
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of(), null, null, null), entry(1, Set.of(), null, null, null), entry(2, Set.of(), null, null, null),
            entry(3, Set.of(), null, null, null), entry(4, Set.of(), null, null, null)
        );

        var readiness = engine.evaluateReadiness(INSTRUMENT_ID, SYMBOL, D0.plusDays(4), history);
        var sequence = engine.evaluateDeliveryLedAccumulation(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(readiness.readiness()).isEqualTo(MarketSequenceReadiness.READY);
        assertThat(sequence).isEmpty(); // READY + no sequence rows = genuinely nothing detected, not "can't tell"
    }

    // ---- sequence strength ----

    @Test
    void sequenceStrengthIncreasesWithEachCompletedStep() {
        List<MarketInflectionHistoryEntry> forming = List.of(
            entry(0, Set.of("DELIVERY_EXPANSION_SUSTAINED_5D", "DELIVERY_RISING"), delivery(0, 90.0, 5, true), null, null)
        );
        List<MarketInflectionHistoryEntry> progressing = List.of(
            entry(0, Set.of("DELIVERY_EXPANSION_SUSTAINED_5D", "DELIVERY_RISING"), delivery(0, 90.0, 5, true), null, null),
            entry(1, Set.of("VOLUME_DELIVERY_UP_PRICE_FLAT"), delivery(1, 90.0, 6, true), relativeVolume(1, 85.0, 2, true), priceReturn(1, 88.0, 1, true))
        );
        List<MarketInflectionHistoryEntry> complete = new ArrayList<>(progressing);
        complete.add(entry(2, Set.of("BREAKOUT_FROM_STEALTH_ACCUMULATION"), null, null, priceReturn(2, 92.0, 0, true)));

        double strength1 = engine.evaluateMarketRecognitionSequence(INSTRUMENT_ID, SYMBOL, forming, DEFAULT_RULES).orElseThrow().sequenceStrength();
        double strength2 = engine.evaluateMarketRecognitionSequence(INSTRUMENT_ID, SYMBOL, progressing, DEFAULT_RULES).orElseThrow().sequenceStrength();
        double strength3 = engine.evaluateMarketRecognitionSequence(INSTRUMENT_ID, SYMBOL, complete, DEFAULT_RULES).orElseThrow().sequenceStrength();

        assertThat(strength1).isLessThan(strength2);
        assertThat(strength2).isLessThan(strength3);
        // 80% completion + 20% persistence; the final BREAKOUT step is a crossing event with 0
        // persistence by Stage 2's own convention, so full completion here is ~80, not 100.
        assertThat(strength3).isCloseTo(80.0, offset(5.0));
    }

    // ---- same-day rerun: pure function, identical input must give identical output ----

    @Test
    void sameHistoryEvaluatedTwiceProducesIdenticalResults() {
        List<MarketInflectionHistoryEntry> history = List.of(
            entry(0, Set.of("DELIVERY_RISING"), delivery(0, 90.0, 1, true), null, null)
        );

        var first = engine.evaluateDeliveryLedAccumulation(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);
        var second = engine.evaluateDeliveryLedAccumulation(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(first).isEqualTo(second);
    }

    // ---- helpers ----

    private static MarketInflectionHistoryEntry entry(
        int dayOffset, Set<String> reasons, MarketEvidenceObservation delivery, MarketEvidenceObservation relativeVolume, MarketEvidenceObservation priceReturn
    ) {
        return new MarketInflectionHistoryEntry(D0.plusDays(dayOffset), SYMBOL, reasons, delivery, relativeVolume, priceReturn);
    }

    private static MarketEvidenceObservation delivery(int dayOffset, double confidence, int persistenceDays, boolean hasPrior) {
        return observation(MarketMetric.DELIVERY_PERCENTAGE_20D_AVG, dayOffset, confidence, persistenceDays, hasPrior);
    }

    private static MarketEvidenceObservation relativeVolume(int dayOffset, double confidence, int persistenceDays, boolean hasPrior) {
        return observation(MarketMetric.RELATIVE_VOLUME, dayOffset, confidence, persistenceDays, hasPrior);
    }

    private static MarketEvidenceObservation priceReturn(int dayOffset, double confidence, int persistenceDays, boolean hasPrior) {
        return observation(MarketMetric.PRICE_RETURN_20D, dayOffset, confidence, persistenceDays, hasPrior);
    }

    private static MarketEvidenceObservation observation(MarketMetric metric, int dayOffset, double confidence, int persistenceDays, boolean hasPrior) {
        LocalDate date = D0.plusDays(dayOffset);
        return new MarketEvidenceObservation(
            metric, INSTRUMENT_ID, SYMBOL, date, hasPrior ? date.minusDays(1) : null,
            BigDecimal.ONE, hasPrior ? BigDecimal.ZERO : null, BigDecimal.ONE, BigDecimal.ONE, persistenceDays, confidence
        );
    }

    private static RuleSet ruleSet(String name1, int value1, String name2, int value2) {
        return new RuleSet(1, List.of(rule(name1, value1), rule(name2, value2)));
    }

    private static Rule rule(String name, int threshold) {
        return new Rule(name, "n/a", 1, List.of(new RuleCondition(RuleOperator.ALWAYS, threshold, threshold)));
    }
}
