package com.alphagraph.corporate.transformation;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CapitalAllocationTransformationSequenceEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate D0 = LocalDate.of(2026, 1, 1);
    private static final RuleSet DEFAULT_RULES = new RuleSet(1, List.of());

    private final CapitalAllocationTransformationSequenceEngine engine = new CapitalAllocationTransformationSequenceEngine();

    // ---- the core trap: change, never value, drives occurrence detection ----

    @Test
    void singleEventOneEightyDayShadowIsNeverReadAsRepetition() {
        List<CapitalAllocationEvidenceObservation> history = List.of(
            obs(0, 1, 1),   // the real entering event
            obs(30, 1, 0),  // window persisting - not a new occurrence
            obs(60, 1, 0),
            obs(90, 1, 0),
            obs(120, 1, 0)
        );

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.FORMING);
        assertThat(result.get().observedOccurrences()).isEqualTo(1);
        assertThat(result.get().currentStep()).isEqualTo(1);
    }

    @Test
    void changeOfPlusOneRegistersOneOccurrence() {
        List<CapitalAllocationEvidenceObservation> history = List.of(obs(0, 1, 1));

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().observedOccurrences()).isEqualTo(1);
        assertThat(result.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.FORMING);
    }

    @Test
    void changeOfPlusTwoOnOneRowRegistersTwoOccurrencesAndCompletesImmediately() {
        List<CapitalAllocationEvidenceObservation> history = List.of(obs(0, 2, 2));

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.COMPLETE);
        assertThat(result.get().observedOccurrences()).isEqualTo(2);
        assertThat(result.get().currentStep()).isEqualTo(2);
        assertThat(result.get().firstStepDate()).isEqualTo(D0);
        assertThat(result.get().lastStepDate()).isEqualTo(D0);
    }

    @Test
    void changeOfZeroRegistersNoOccurrenceAndProducesNoResult() {
        List<CapitalAllocationEvidenceObservation> history = List.of(obs(0, 0, 0));

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isEmpty();
    }

    @Test
    void negativeChangeRegistersNoOccurrenceAndProducesNoResult() {
        List<CapitalAllocationEvidenceObservation> history = List.of(obs(0, 0, -1));

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isEmpty();
    }

    // ---- once COMPLETE, stay COMPLETE (round-1 correction) ----

    @Test
    void thirdOccurrenceWithinWindowAfterCompleteStaysCompleteAndRefreshesLastStepDate() {
        List<CapitalAllocationEvidenceObservation> history = List.of(
            obs(0, 1, 1),
            obs(90, 1, 1),
            obs(100, 1, 1)
        );

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.COMPLETE);
        assertThat(result.get().observedOccurrences()).isEqualTo(3);
        assertThat(result.get().currentStep()).isEqualTo(2); // pinned at requiredRepeats, never exceeds it
        assertThat(result.get().firstStepDate()).isEqualTo(D0);
        assertThat(result.get().lastStepDate()).isEqualTo(D0.plusDays(100));
    }

    @Test
    void occurrenceArrivingAfterGapExceededOnceCompleteStartsFreshCluster() {
        List<CapitalAllocationEvidenceObservation> history = List.of(
            obs(0, 1, 1),
            obs(90, 1, 1),   // COMPLETE
            obs(300, 1, 1)   // gap since day 90 is 210 > 180 - old cluster's window lapsed
        );

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.FORMING);
        assertThat(result.get().observedOccurrences()).isEqualTo(1);
        assertThat(result.get().firstStepDate()).isEqualTo(D0.plusDays(300));
        assertThat(result.get().lastStepDate()).isEqualTo(D0.plusDays(300));
    }

    // ---- a COMPLETE cluster also expires from silence alone (round-2 correction) ----

    @Test
    void completeClusterStaysCompleteDuringSilenceShorterThanTheWindow() {
        List<CapitalAllocationEvidenceObservation> history = List.of(
            obs(0, 1, 1),
            obs(90, 1, 1),    // COMPLETE
            obs(190, 1, 0)    // 100 days of silence since day 90 - still within the 180-day window
        );

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.COMPLETE);
        assertThat(result.get().lastStepDate()).isEqualTo(D0.plusDays(90)); // unchanged - no new occurrence, just elapsed time
    }

    @Test
    void completeClusterExpiresAfterSilenceExceedsTheWindowWithNoNewOccurrence() {
        List<CapitalAllocationEvidenceObservation> history = List.of(
            obs(0, 1, 1),
            obs(90, 1, 1),    // COMPLETE
            obs(271, 1, 0)    // 181 days of silence since day 90 - exceeds the 180-day window, no 3rd occurrence ever arrived
        );

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.BROKEN);
        assertThat(result.get().reasons()).extracting("code").contains("SEQUENCE_EXPIRED");
    }

    @Test
    void newOccurrenceAfterSilenceExpiryStartsFreshFormingClusterNotACompleteContinuation() {
        List<CapitalAllocationEvidenceObservation> history = List.of(
            obs(0, 1, 1),
            obs(90, 1, 1),    // COMPLETE
            obs(271, 1, 0),   // no occurrence this day - just present so a real gap elapses
            obs(300, 1, 1)    // a genuinely new occurrence, 210 days after day 90's - past the repeat window, so this starts fresh rather than extending the old COMPLETE cluster
        );

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.FORMING);
        assertThat(result.get().observedOccurrences()).isEqualTo(1);
        assertThat(result.get().firstStepDate()).isEqualTo(D0.plusDays(300));
    }

    // ---- generic Stage 3 cases (docs/008 §21) ----

    @Test
    void twoOccurrencesWithinTheWindowComplete() {
        List<CapitalAllocationEvidenceObservation> history = List.of(
            obs(0, 1, 1),
            obs(90, 1, 1)
        );

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.COMPLETE);
    }

    @Test
    void progressesThroughAnIntermediateStepWithAHigherRepeatThreshold() {
        RuleSet threeRepeatRule = ruleSet("stage3-capital-return-repeat-min-count", 3);
        List<CapitalAllocationEvidenceObservation> history = List.of(
            obs(0, 1, 1),
            obs(50, 1, 1)
        );

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, threeRepeatRule);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.PROGRESSING);
        assertThat(result.get().currentStep()).isEqualTo(2);
        assertThat(result.get().totalSteps()).isEqualTo(3);
    }

    @Test
    void gapExceededBeforeEverCompletingBreaksTheAttempt() {
        List<CapitalAllocationEvidenceObservation> history = List.of(
            obs(0, 1, 1),
            obs(200, 1, 0) // no occurrence, just anchors "today" 200 days after the only real occurrence - gap 200 > 180
        );

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.BROKEN);
    }

    @Test
    void noEvidenceProducesNoResultAtAll() {
        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, List.of(), DEFAULT_RULES);

        assertThat(result).isEmpty();
    }

    @Test
    void ruleVersionIsStamped() {
        List<CapitalAllocationEvidenceObservation> history = List.of(obs(0, 1, 1));

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().ruleVersion()).isEqualTo(1);
    }

    @Test
    void evaluatingTheSameHistoryTwiceIsIdempotent() {
        List<CapitalAllocationEvidenceObservation> history = List.of(obs(0, 1, 1), obs(90, 1, 1));

        var first = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);
        var second = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(first).isEqualTo(second);
    }

    // ---- disclosed limitation: change is a NET figure ----

    @Test
    void exactOneEightyDayOffsetCancellationHidesTheSecondEvent() {
        // Day 0: one real entering event, change=+1 (value 0 -> 1).
        // Day 180: a second real entering event's exDate lands exactly 180 days later, exactly as
        // day 0's own event exits the rolling window - entry (+1) and exit (-1) net to 0. This is
        // the disclosed, accepted limitation (see the engine's own javadoc) - not solved here.
        List<CapitalAllocationEvidenceObservation> history = List.of(
            obs(0, 1, 1),
            obs(180, 1, 0)
        );

        var result = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().observedOccurrences()).isEqualTo(1); // the second real event is missed - documented, not a bug to fix here
        assertThat(result.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.FORMING);
    }

    // ---- buyback + equity raise are fully independent ----

    @Test
    void buybackAndEquityRaiseBothActiveOnOverlappingDatesProduceTwoIndependentResults() {
        List<CapitalAllocationEvidenceObservation> buybackHistory = List.of(obs(0, 1, 1), obs(90, 1, 1));
        List<CapitalAllocationEvidenceObservation> equityRaiseHistory = List.of(equityRaiseObs(0, 1, 1), equityRaiseObs(90, 1, 1));

        var capitalReturn = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, buybackHistory, DEFAULT_RULES);
        var equityRaise = engine.evaluateRepeatedEquityRaise(INSTRUMENT_ID, SYMBOL, equityRaiseHistory, DEFAULT_RULES);

        assertThat(capitalReturn).isPresent();
        assertThat(equityRaise).isPresent();
        assertThat(capitalReturn.get().sequenceType()).isEqualTo(CapitalAllocationSequenceType.REPEATED_CAPITAL_RETURN);
        assertThat(equityRaise.get().sequenceType()).isEqualTo(CapitalAllocationSequenceType.REPEATED_EQUITY_RAISE);
        assertThat(capitalReturn.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.COMPLETE);
        assertThat(equityRaise.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.COMPLETE);
    }

    /** Decision 3, proven concretely: on a day both metrics independently have change > 0 (a real Stage 2 MIXED_CAPITAL_ALLOCATION_ACTIVITY day), the equity-raise walk still counts its own occurrence directly from Stage 1 evidence - never starved by buyback also firing. */
    @Test
    void mixedStateDayDoesNotStarveTheEquityRaiseWalk() {
        List<CapitalAllocationEvidenceObservation> equityRaiseHistory = List.of(
            equityRaiseObs(0, 1, 1),
            equityRaiseObs(50, 1, 1) // same day buyback also has change > 0, per buybackAndEquityRaise... above, but evaluated fully independently here
        );

        var result = engine.evaluateRepeatedEquityRaise(INSTRUMENT_ID, SYMBOL, equityRaiseHistory, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(CapitalAllocationSequencePhase.COMPLETE);
        assertThat(result.get().observedOccurrences()).isEqualTo(2);
    }

    // ---- backfill point-in-time correctness ----

    @Test
    void boundedHistoryNeverLeaksALaterRealOccurrence() {
        // Day 200 is still within 180 days of day 90's occurrence, so it extends the same cluster
        // (observedOccurrences 2 -> 3) rather than starting a fresh one - isolating this test to
        // the point-in-time-boundary question alone, not the separate gap-expiry behavior already
        // covered by the COMPLETE-cluster-expiry tests above.
        List<CapitalAllocationEvidenceObservation> fullHistory = List.of(obs(0, 1, 1), obs(90, 1, 1), obs(200, 1, 1));
        List<CapitalAllocationEvidenceObservation> boundedToDay90 = List.of(obs(0, 1, 1), obs(90, 1, 1));

        var boundedResult = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, boundedToDay90, DEFAULT_RULES);
        var fullResult = engine.evaluateRepeatedCapitalReturn(INSTRUMENT_ID, SYMBOL, fullHistory, DEFAULT_RULES);

        assertThat(boundedResult).isPresent();
        assertThat(boundedResult.get().asOfDate()).isEqualTo(D0.plusDays(90));
        assertThat(boundedResult.get().lastStepDate()).isEqualTo(D0.plusDays(90));
        assertThat(boundedResult.get().observedOccurrences()).isEqualTo(2);

        // The full, unbounded evaluation sees the later real occurrence too - bounded and full
        // evaluations of the same underlying data genuinely differ, proving nothing from beyond
        // the bound leaked into the bounded one.
        assertThat(fullResult).isPresent();
        assertThat(fullResult.get().observedOccurrences()).isEqualTo(3);
    }

    // ---- readiness: day-coverage, not row count (round-1 correction 3) ----

    @Test
    void tinyRowCountOneDayApartIsInsufficientHistory() {
        List<CapitalAllocationEvidenceObservation> buybackHistory = List.of(obs(0, 1, 1), obs(1, 1, 0));

        var readiness = engine.evaluateReadiness(INSTRUMENT_ID, SYMBOL, D0.plusDays(1), buybackHistory, List.of(), false, DEFAULT_RULES);

        assertThat(readiness.readiness()).isEqualTo(CapitalAllocationSequenceReadiness.INSUFFICIENT_HISTORY);
        assertThat(readiness.historyDays()).isEqualTo(1);
    }

    @Test
    void coverageMeetingTheRepeatWindowIsReadyEvenWithFewRows() {
        List<CapitalAllocationEvidenceObservation> buybackHistory = List.of(obs(0, 1, 1), obs(200, 1, 0));

        var readiness = engine.evaluateReadiness(INSTRUMENT_ID, SYMBOL, D0.plusDays(200), buybackHistory, List.of(), false, DEFAULT_RULES);

        assertThat(readiness.readiness()).isEqualTo(CapitalAllocationSequenceReadiness.READY);
        assertThat(readiness.historyDays()).isEqualTo(200);
    }

    @Test
    void anyCompleteSequenceIsReadyRegardlessOfCoverage() {
        List<CapitalAllocationEvidenceObservation> buybackHistory = List.of(obs(0, 2, 2)); // completes same-day, 0 days of coverage

        var readiness = engine.evaluateReadiness(INSTRUMENT_ID, SYMBOL, D0, buybackHistory, List.of(), true, DEFAULT_RULES);

        assertThat(readiness.readiness()).isEqualTo(CapitalAllocationSequenceReadiness.READY);
    }

    // ---- readiness: gated by the least-covered applicable metric (round-2 correction) ----

    @Test
    void readinessIsGatedByTheLeastCoveredMetricWhenCoverageDiverges() {
        List<CapitalAllocationEvidenceObservation> buybackHistory = List.of(obs(0, 1, 1), obs(220, 1, 0));      // 220 days of coverage
        List<CapitalAllocationEvidenceObservation> equityRaiseHistory = List.of(equityRaiseObs(180, 1, 1), equityRaiseObs(220, 1, 0)); // only 40 days of coverage

        var readiness = engine.evaluateReadiness(INSTRUMENT_ID, SYMBOL, D0.plusDays(220), buybackHistory, equityRaiseHistory, false, DEFAULT_RULES);

        assertThat(readiness.historyDays()).isEqualTo(40);
        assertThat(readiness.readiness()).isEqualTo(CapitalAllocationSequenceReadiness.INSUFFICIENT_HISTORY);
    }

    @Test
    void anInapplicableMetricWithNoEvidenceEverDoesNotDragDownReadiness() {
        List<CapitalAllocationEvidenceObservation> buybackHistory = List.of(obs(0, 1, 1), obs(220, 1, 0));
        List<CapitalAllocationEvidenceObservation> equityRaiseHistory = List.of(); // this instrument has simply never done a rights issue

        var readiness = engine.evaluateReadiness(INSTRUMENT_ID, SYMBOL, D0.plusDays(220), buybackHistory, equityRaiseHistory, false, DEFAULT_RULES);

        assertThat(readiness.historyDays()).isEqualTo(220);
        assertThat(readiness.readiness()).isEqualTo(CapitalAllocationSequenceReadiness.READY);
    }

    // ---- helpers ----

    private static CapitalAllocationEvidenceObservation obs(int dayOffset, int value, int change) {
        return observation(CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D, dayOffset, value, change);
    }

    private static CapitalAllocationEvidenceObservation equityRaiseObs(int dayOffset, int value, int change) {
        return observation(CapitalAllocationMetric.EQUITY_RAISE_EVENT_COUNT_180D, dayOffset, value, change);
    }

    private static CapitalAllocationEvidenceObservation observation(CapitalAllocationMetric metric, int dayOffset, int value, int change) {
        LocalDate asOfDate = D0.plusDays(dayOffset);
        return new CapitalAllocationEvidenceObservation(
            metric, INSTRUMENT_ID, SYMBOL, asOfDate, asOfDate.minusDays(1),
            value, value - change, change, change, 1, 90.0, 180
        );
    }

    private static RuleSet ruleSet(String ruleName, int threshold) {
        RuleCondition condition = new RuleCondition(RuleOperator.ALWAYS, threshold, null, threshold);
        return new RuleSet(1, List.of(new Rule(ruleName, "requiredRepeats", 1, List.of(condition))));
    }
}
