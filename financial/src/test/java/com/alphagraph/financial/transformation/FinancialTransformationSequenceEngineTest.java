package com.alphagraph.financial.transformation;

import com.alphagraph.common.rules.RuleSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class FinancialTransformationSequenceEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "TCS";
    private static final LocalDate Q0 = LocalDate.of(2024, 6, 30);
    private static final RuleSet DEFAULT_RULES = new RuleSet(1, List.of());

    private final FinancialTransformationSequenceEngine engine = new FinancialTransformationSequenceEngine();

    // ---- BUSINESS_ACCELERATION_CYCLE ----

    @Test
    void revenueOnlyIsForming() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null)
        );

        var result = engine.evaluateBusinessAccelerationCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.FORMING);
        assertThat(result.get().currentStep()).isEqualTo(1);
        assertThat(result.get().reasons()).extracting("code").contains("REVENUE_GROWTH_IMPROVING");
    }

    @Test
    void threeConsecutivePeriodsWithStrongThirdVelocityCompletes() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null),
            entry(1, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(1, 90.0, 2, true), null, null, null),
            entry(2, reasons("REVENUE_GROWTH_IMPROVING", 3.0), revenue(2, 90.0, 3, true), null, null, null) // 3.0 >= STRONG_THRESHOLD (2.0)
        );

        var result = engine.evaluateBusinessAccelerationCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.COMPLETE);
        assertThat(result.get().currentStep()).isEqualTo(3);
        assertThat(result.get().firstStepDate()).isEqualTo(Q0);
        assertThat(result.get().lastStepDate()).isEqualTo(Q0.plusMonths(6));
    }

    @Test
    void weakFinalOccurrenceIsTreatedAsANonMatchAndBreaksAtZeroGapTolerance() {
        // The velocity gate failing on the would-be-final step is a non-match, not an automatic
        // COMPLETE - but with the default zero gap tolerance, any non-match (gate failure or plain
        // silence) immediately breaks the attempt too, since there's no tolerance window left to
        // "wait" in.
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null),
            entry(1, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(1, 90.0, 2, true), null, null, null),
            entry(2, reasons("REVENUE_GROWTH_IMPROVING", 0.1), revenue(2, 90.0, 3, true), null, null, null) // WEAK - gate fails
        );

        var result = engine.evaluateBusinessAccelerationCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.BROKEN);
    }

    @Test
    void aFreshRunAfterABreakStillCompletesIndependently() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null),
            entry(1, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(1, 90.0, 2, true), null, null, null),
            entry(2, reasons("REVENUE_GROWTH_IMPROVING", 0.1), revenue(2, 90.0, 3, true), null, null, null), // WEAK - breaks the first attempt
            entry(3, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(3, 90.0, 1, true), null, null, null), // a brand-new attempt starts here
            entry(4, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(4, 90.0, 2, true), null, null, null),
            entry(5, reasons("REVENUE_GROWTH_IMPROVING", 3.0), revenue(5, 90.0, 3, true), null, null, null) // STRONG - completes the fresh attempt
        );

        var result = engine.evaluateBusinessAccelerationCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.COMPLETE);
        // the fresh attempt's own dates, never the abandoned first cycle's
        assertThat(result.get().firstStepDate()).isEqualTo(Q0.plusMonths(9));
        assertThat(result.get().lastStepDate()).isEqualTo(Q0.plusMonths(15));
    }

    @Test
    void aSingleGapQuarterHardBreaksTheSequence() {
        // stage3-business-acceleration-max-gap defaults to 0 - strictly consecutive. The history
        // ends right on the silent quarter (no subsequent qualifying entry, which would just start
        // a brand-new attempt and overwrite the BROKEN result under test).
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null),
            entry(1, Map.of(), null, null, null, null) // one silent quarter - breaks it
        );

        var result = engine.evaluateBusinessAccelerationCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.BROKEN);
        assertThat(result.get().reasons()).extracting("code").contains("SEQUENCE_EXPIRED");
    }

    @Test
    void velocityGateDefensiveNullSafety() {
        // A REVENUE_GROWTH_IMPROVING-flagged entry with no metric_value recorded - should never
        // happen against real data, but must not crash; treated as a non-match for the gate.
        Map<String, Double> reasonsWithNullValue = new HashMap<>();
        reasonsWithNullValue.put("REVENUE_GROWTH_IMPROVING", null);
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null),
            entry(1, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(1, 90.0, 2, true), null, null, null),
            new FinancialInflectionHistoryEntry(Q0.plusMonths(6), SYMBOL, reasonsWithNullValue, revenue(2, 90.0, 3, true), null, null, null)
        );

        var result = engine.evaluateBusinessAccelerationCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isNotEqualTo(FinancialSequencePhase.COMPLETE);
    }

    @Test
    void neverFiringProducesNoResultAtAll() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, Map.of(), null, null, null, null),
            entry(1, Map.of(), null, null, null, null)
        );

        var result = engine.evaluateBusinessAccelerationCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isEmpty();
    }

    // ---- OPERATING_LEVERAGE_CYCLE ----

    @Test
    void revenueOnlyIsFormingForOperatingLeverage() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null)
        );

        var result = engine.evaluateOperatingLeverageCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.FORMING);
        assertThat(result.get().currentStep()).isEqualTo(1);
    }

    @Test
    void revenueThenMarginOnlyProgresses() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null),
            entry(1, reasons("MARGIN_EXPANDING_SUSTAINED", 1.0), null, null, margin(1, 80.0, 1, true), null)
        );

        var result = engine.evaluateOperatingLeverageCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.PROGRESSING);
        assertThat(result.get().currentStep()).isEqualTo(2);
    }

    @Test
    void allThreeWithinWindowCompletes() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null),
            entry(1, reasons("MARGIN_EXPANDING_SUSTAINED", 1.0), null, null, margin(1, 80.0, 1, true), null),
            entry(2, reasons("PAT_GROWTH_IMPROVING", 1.0), null, pat(2, 85.0, 1, true), null, null)
        );

        var result = engine.evaluateOperatingLeverageCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.COMPLETE);
        assertThat(result.get().reasons()).extracting("code").contains(
            "REVENUE_GROWTH_IMPROVING", "MARGIN_EXPANDING_SUSTAINED", "PAT_GROWTH_IMPROVING", "ALL_REQUIRED_STEPS_COMPLETE"
        );
    }

    @Test
    void patYearsBeforeRevenueMustNotCompleteOrRetroactivelyCount() {
        List<FinancialInflectionHistoryEntry> history = new ArrayList<>();
        history.add(entry(0, reasons("PAT_GROWTH_IMPROVING", 1.0), null, pat(0, 85.0, 1, true), null, null)); // PAT years before revenue
        for (int i = 1; i < 20; i++) {
            history.add(entry(i, Map.of(), null, null, null, null));
        }
        history.add(entry(20, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(20, 90.0, 1, true), null, null, null)); // revenue finally starts
        history.add(entry(21, reasons("MARGIN_EXPANDING_SUSTAINED", 1.0), null, null, margin(21, 80.0, 1, true), null));

        var result = engine.evaluateOperatingLeverageCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        // the attempt must be anchored at revenue's own real start (index 20), never the stale PAT date
        assertThat(result.get().firstStepDate()).isEqualTo(Q0.plusMonths(20L * 3));
        assertThat(result.get().sequencePhase()).isNotEqualTo(FinancialSequencePhase.COMPLETE);
    }

    @Test
    void marginAndPatLandingTheSamePeriodBothLatchAndComplete() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null),
            entry(1, reasonsOf(Map.of("MARGIN_EXPANDING_SUSTAINED", 1.0, "PAT_GROWTH_IMPROVING", 1.0)), null, pat(1, 85.0, 1, true), margin(1, 80.0, 1, true), null)
        );

        var result = engine.evaluateOperatingLeverageCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.COMPLETE);
        assertThat(result.get().lastStepDate()).isEqualTo(Q0.plusMonths(3));
    }

    @Test
    void allThreeInTheSameSinglePeriodCompletesGracefully() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasonsOf(Map.of("REVENUE_GROWTH_IMPROVING", 1.0, "MARGIN_EXPANDING_SUSTAINED", 1.0, "PAT_GROWTH_IMPROVING", 1.0)),
                revenue(0, 90.0, 1, true), pat(0, 85.0, 1, true), margin(0, 80.0, 1, true), null)
        );

        var result = engine.evaluateOperatingLeverageCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.COMPLETE);
        assertThat(result.get().firstStepDate()).isEqualTo(result.get().lastStepDate());
    }

    @Test
    void operatingLeverageGapExceededBeforeBothArriveBreaks() {
        // default stage3-earnings-cycle-max-quarter-gap is 4
        List<FinancialInflectionHistoryEntry> history = new ArrayList<>();
        history.add(entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null));
        for (int i = 1; i <= 4; i++) {
            history.add(entry(i, Map.of(), null, null, null, null));
        }
        history.add(entry(5, reasons("MARGIN_EXPANDING_SUSTAINED", 1.0), null, null, margin(5, 80.0, 1, true), null)); // too late, gap=5 > 4

        var result = engine.evaluateOperatingLeverageCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.BROKEN);
        assertThat(result.get().reasons()).extracting("code").contains("SEQUENCE_EXPIRED");
    }

    // ---- MULTI_QUARTER_EARNINGS_EXPANSION: independent streaks, never combined ----

    @Test
    void patTwoConsecutiveQuartersCompletes() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("PAT_GROWTH_IMPROVING", 1.0), null, pat(0, 85.0, 1, true), null, null),
            entry(1, reasons("PAT_GROWTH_IMPROVING", 1.0), null, pat(1, 85.0, 2, true), null, null)
        );

        var result = engine.evaluateMultiQuarterEarningsExpansion(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.COMPLETE);
        assertThat(result.get().reasons()).extracting("code").contains("PAT_GROWTH_IMPROVING");
        assertThat(result.get().reasons()).extracting("code").doesNotContain("MARGIN_EXPANDING_SUSTAINED");
    }

    @Test
    void marginTwoConsecutiveQuartersCompletes() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("MARGIN_EXPANDING_SUSTAINED", 1.0), null, null, margin(0, 80.0, 1, true), null),
            entry(1, reasons("MARGIN_EXPANDING_SUSTAINED", 1.0), null, null, margin(1, 80.0, 2, true), null)
        );

        var result = engine.evaluateMultiQuarterEarningsExpansion(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.COMPLETE);
        assertThat(result.get().reasons()).extracting("code").contains("MARGIN_EXPANDING_SUSTAINED");
    }

    @Test
    void patAndMarginBothTwoConsecutiveQuartersCompletesAndShowsBothCodes() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasonsOf(Map.of("PAT_GROWTH_IMPROVING", 1.0, "MARGIN_EXPANDING_SUSTAINED", 1.0)), null, pat(0, 85.0, 1, true), margin(0, 80.0, 1, true), null),
            entry(1, reasonsOf(Map.of("PAT_GROWTH_IMPROVING", 1.0, "MARGIN_EXPANDING_SUSTAINED", 1.0)), null, pat(1, 85.0, 2, true), margin(1, 80.0, 2, true), null)
        );

        var result = engine.evaluateMultiQuarterEarningsExpansion(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.COMPLETE);
        assertThat(result.get().reasons()).extracting("code").contains("PAT_GROWTH_IMPROVING", "MARGIN_EXPANDING_SUSTAINED");
    }

    @Test
    void alternatingPatThenMarginNeverManufacturesPersistence() {
        // THE core correction: PAT quarter 1, margin quarter 2 - neither metric itself persisted.
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("PAT_GROWTH_IMPROVING", 1.0), null, pat(0, 85.0, 1, true), null, null),
            entry(1, reasons("MARGIN_EXPANDING_SUSTAINED", 1.0), null, null, margin(1, 80.0, 1, true), null)
        );

        var result = engine.evaluateMultiQuarterEarningsExpansion(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        // Never COMPLETE - at most a fresh 1-step FORMING reflecting only the most recent metric (margin).
        if (result.isPresent()) {
            assertThat(result.get().sequencePhase()).isNotEqualTo(FinancialSequencePhase.COMPLETE);
            assertThat(result.get().currentStep()).isEqualTo(1);
        }
    }

    @Test
    void aSingleQuietQuarterResetsTheStreakAndAFreshStreakStartsCleanly() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("PAT_GROWTH_IMPROVING", 1.0), null, pat(0, 85.0, 1, true), null, null),
            entry(1, Map.of(), null, null, null, null), // quiet quarter - resets the PAT streak
            entry(2, reasons("PAT_GROWTH_IMPROVING", 1.0), null, pat(2, 85.0, 1, true), null, null),
            entry(3, reasons("PAT_GROWTH_IMPROVING", 1.0), null, pat(3, 85.0, 2, true), null, null)
        );

        var result = engine.evaluateMultiQuarterEarningsExpansion(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.COMPLETE);
        // the fresh streak started at index 2, not index 0 - the old count was not resumed
        assertThat(result.get().firstStepDate()).isEqualTo(Q0.plusMonths(6));
    }

    // ---- INTEREST_COST_RELIEF_TREND ----

    @Test
    void interestReliefThreeConsecutivePeriodsCompletes() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("INTEREST_EXPENSE_FALLING", -5.0), null, null, null, interest(0, 75.0, 1, true)),
            entry(1, reasons("INTEREST_EXPENSE_FALLING", -6.0), null, null, null, interest(1, 75.0, 2, true)),
            entry(2, reasons("INTEREST_EXPENSE_FALLING", -7.0), null, null, null, interest(2, 75.0, 3, true))
        );

        var result = engine.evaluateInterestCostReliefTrend(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.COMPLETE);
        assertThat(result.get().currentStep()).isEqualTo(3);
    }

    @Test
    void interestReliefSingleGapHardBreaks() {
        // History ends right on the silent quarter - a subsequent qualifying entry would just
        // start a brand-new attempt, overwriting the BROKEN result under test.
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("INTEREST_EXPENSE_FALLING", -5.0), null, null, null, interest(0, 75.0, 1, true)),
            entry(1, Map.of(), null, null, null, null)
        );

        var result = engine.evaluateInterestCostReliefTrend(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().sequencePhase()).isEqualTo(FinancialSequencePhase.BROKEN);
    }

    // ---- readiness: independent of whether any sequence fired ----

    @Test
    void readinessIsInsufficientHistoryBelowThreePeriods() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null),
            entry(1, Map.of(), null, null, null, null)
        );

        var readiness = engine.evaluateReadiness(INSTRUMENT_ID, SYMBOL, Q0.plusMonths(3), history);

        assertThat(readiness.readiness()).isEqualTo(FinancialSequenceReadiness.INSUFFICIENT_HISTORY);
        assertThat(readiness.historyPeriods()).isEqualTo(2);
    }

    @Test
    void readinessIsReadyAtThreeOrMorePeriodsEvenWhenNoSequenceFires() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, Map.of(), null, null, null, null), entry(1, Map.of(), null, null, null, null), entry(2, Map.of(), null, null, null, null)
        );

        var readiness = engine.evaluateReadiness(INSTRUMENT_ID, SYMBOL, Q0.plusMonths(6), history);
        var sequence = engine.evaluateBusinessAccelerationCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(readiness.readiness()).isEqualTo(FinancialSequenceReadiness.READY);
        assertThat(sequence).isEmpty();
    }

    // ---- idempotency ----

    @Test
    void sameHistoryEvaluatedTwiceProducesIdenticalResults() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null)
        );

        var first = engine.evaluateBusinessAccelerationCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);
        var second = engine.evaluateBusinessAccelerationCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(first).isEqualTo(second);
    }

    // ---- backfill point-in-time correctness (truncated history never sees later evidence) ----

    @Test
    void aTruncatedHistoryNeverSeesLaterEvidence() {
        List<FinancialInflectionHistoryEntry> full = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 90.0, 1, true), null, null, null),
            entry(1, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(1, 90.0, 2, true), null, null, null),
            entry(2, reasons("REVENUE_GROWTH_IMPROVING", 3.0), revenue(2, 90.0, 3, true), null, null, null)
        );
        List<FinancialInflectionHistoryEntry> truncatedAtStep1 = full.subList(0, 1);

        var fullResult = engine.evaluateBusinessAccelerationCycle(INSTRUMENT_ID, SYMBOL, full, DEFAULT_RULES);
        var truncatedResult = engine.evaluateBusinessAccelerationCycle(INSTRUMENT_ID, SYMBOL, truncatedAtStep1, DEFAULT_RULES);

        assertThat(fullResult.get().sequencePhase()).isEqualTo(FinancialSequencePhase.COMPLETE);
        assertThat(truncatedResult.get().sequencePhase()).isEqualTo(FinancialSequencePhase.FORMING);
        assertThat(truncatedResult.get().currentStep()).isEqualTo(1);
    }

    // ---- confidence sanity check ----

    @Test
    void confidenceIsSourcedFromRealStage1EvidenceNotStage2Numbers() {
        List<FinancialInflectionHistoryEntry> history = List.of(
            entry(0, reasons("REVENUE_GROWTH_IMPROVING", 1.0), revenue(0, 70.0, 1, true), null, null, null)
        );

        var result = engine.evaluateBusinessAccelerationCycle(INSTRUMENT_ID, SYMBOL, history, DEFAULT_RULES);

        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isCloseTo(70.0, offset(0.01));
    }

    // ---- helpers ----

    private static FinancialInflectionHistoryEntry entry(
        int quarterOffset, Map<String, Double> reasonValues,
        FinancialEvidenceObservation revenue, FinancialEvidenceObservation pat,
        FinancialEvidenceObservation margin, FinancialEvidenceObservation interest
    ) {
        LocalDate periodEnd = Q0.plusMonths(3L * quarterOffset);
        return new FinancialInflectionHistoryEntry(periodEnd, SYMBOL, reasonValues, revenue, pat, margin, interest);
    }

    private static Map<String, Double> reasons(String code, double value) {
        Map<String, Double> map = new HashMap<>();
        map.put(code, value);
        return map;
    }

    private static Map<String, Double> reasonsOf(Map<String, Double> values) {
        return new HashMap<>(values);
    }

    private static FinancialEvidenceObservation revenue(int quarterOffset, double confidence, int persistenceQuarters, boolean hasPrior) {
        return observation(FinancialMetric.REVENUE, quarterOffset, confidence, persistenceQuarters, hasPrior);
    }

    private static FinancialEvidenceObservation pat(int quarterOffset, double confidence, int persistenceQuarters, boolean hasPrior) {
        return observation(FinancialMetric.PAT, quarterOffset, confidence, persistenceQuarters, hasPrior);
    }

    private static FinancialEvidenceObservation margin(int quarterOffset, double confidence, int persistenceQuarters, boolean hasPrior) {
        return observation(FinancialMetric.OPERATING_MARGIN, quarterOffset, confidence, persistenceQuarters, hasPrior);
    }

    private static FinancialEvidenceObservation interest(int quarterOffset, double confidence, int persistenceQuarters, boolean hasPrior) {
        return observation(FinancialMetric.INTEREST_EXPENSE, quarterOffset, confidence, persistenceQuarters, hasPrior);
    }

    private static FinancialEvidenceObservation observation(FinancialMetric metric, int quarterOffset, double confidence, int persistenceQuarters, boolean hasPrior) {
        LocalDate periodEnd = Q0.plusMonths(3L * quarterOffset);
        return new FinancialEvidenceObservation(
            metric, INSTRUMENT_ID, SYMBOL, periodEnd, hasPrior ? periodEnd.minusMonths(3) : null,
            BigDecimal.ONE, hasPrior ? BigDecimal.ZERO : null, BigDecimal.ONE, BigDecimal.ONE, persistenceQuarters, confidence, "YOY"
        );
    }
}
