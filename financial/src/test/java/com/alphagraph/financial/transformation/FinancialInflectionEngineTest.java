package com.alphagraph.financial.transformation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class FinancialInflectionEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate Q1 = LocalDate.of(2025, 3, 31);
    private static final LocalDate Q2 = LocalDate.of(2025, 6, 30);
    private static final LocalDate Q3 = LocalDate.of(2025, 9, 30);

    private final FinancialInflectionEngine engine = new FinancialInflectionEngine();

    @Test
    void revenueAccelerationFiresOnAConsistentlyDerivedSeries_ignoringMixedStage1Comparators() {
        // Raw values 100 -> 110 -> 126.5 -> 151.8 give clean QoQ growth rates 10% -> 15% -> 20%,
        // genuinely accelerating both transitions. Each row's own change/comparatorUsed is
        // deliberately set to misleading, inconsistent values (a fake YOY change on row0, fake
        // QOQ_ONLY changes elsewhere with irrelevant priorValues) to prove the engine never reads
        // those fields for this computation - only value() and the oldest row's priorValue().
        var row0 = obs(Q1, null, "110", "100", "-999", 0, "YOY");
        var row1 = obs(Q2, Q1, "126.5", "999999", "-999", 0, "QOQ_ONLY");
        var row2 = obs(Q3, Q2, "151.8", "999999", "-999", 0, "QOQ_ONLY");

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, List.of(row0, row1, row2), List.of(), null);

        assertThat(result.primaryState()).isEqualTo(FinancialInflectionState.REVENUE_ACCELERATION);
        assertThat(result.drivingMetric()).isEqualTo(FinancialMetric.REVENUE);
        assertThat(result.level().doubleValue()).isCloseTo(20.0, offset(0.01));
        assertThat(result.change().doubleValue()).isCloseTo(5.0, offset(0.01));
        assertThat(result.persistence()).isEqualTo(2);
        assertThat(result.asOfDate()).isEqualTo(Q3);
        assertThat(result.confidence()).isEqualTo(79.0); // 75 + min(10, 2*2)
    }

    @Test
    void revenueAccelerationDoesNotFireWhenGrowthIsDecelerating() {
        // 100 -> 120 -> 132 -> 137.28: growth rates 20% -> 10% -> 4% - decelerating, not accelerating.
        var row0 = obs(Q1, null, "120", "100", null, 0, null);
        var row1 = obs(Q2, Q1, "132", null, null, 0, null);
        var row2 = obs(Q3, Q2, "137.28", null, null, 0, null);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, List.of(row0, row1, row2), List.of(), null);

        assertThat(result.primaryState()).isEqualTo(FinancialInflectionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void structuralMarginExpansionReusesStage1sPersistenceDirectly() {
        var margin = obs(Q3, Q2, "12.50", "11.00", "1.50", 3, "QOQ_ONLY");

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, List.of(), List.of(), margin);

        assertThat(result.primaryState()).isEqualTo(FinancialInflectionState.STRUCTURAL_MARGIN_EXPANSION);
        assertThat(result.drivingMetric()).isEqualTo(FinancialMetric.OPERATING_MARGIN);
        assertThat(result.level()).isEqualByComparingTo("12.50");
        assertThat(result.change()).isEqualByComparingTo("1.50");
        assertThat(result.persistence()).isEqualTo(3);
    }

    @Test
    void structuralMarginExpansionRequiresPersistenceOfAtLeastThree() {
        var margin = obs(Q3, Q2, "12.50", "11.00", "1.50", 2, "QOQ_ONLY");

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, List.of(), List.of(), margin);

        assertThat(result.primaryState()).isEqualTo(FinancialInflectionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void operatingLeverageInflectionFiresOnRealDerivedOperatingProfitGrowth() {
        // revenue 900->1000 (+11.11%), margin 8.0%->10.0% => operating profit 72->100 (+38.89%),
        // pat 60->90 (+50%). 11.11% < 38.89% < 50% - genuine operating leverage.
        var revenue = List.of(obs(Q3, Q2, "1000", "900", "100", 0, "QOQ_ONLY"));
        var pat = List.of(obs(Q3, Q2, "90", "60", "30", 0, "QOQ_ONLY"));
        var margin = obs(Q3, Q2, "10.0", "8.0", "2.0", 0, "QOQ_ONLY");

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, revenue, pat, margin);

        assertThat(result.primaryState()).isEqualTo(FinancialInflectionState.OPERATING_LEVERAGE_INFLECTION);
        assertThat(result.drivingMetric()).isEqualTo(FinancialMetric.PAT);
        assertThat(result.level().doubleValue()).isCloseTo(50.0, offset(0.01));
        assertThat(result.change().doubleValue()).isCloseTo(38.89, offset(0.05)); // 50 - 11.11
    }

    @Test
    void operatingLeverageInflectionRequiresAllThreePeriodsToAlign() {
        var revenue = List.of(obs(Q3, Q2, "1000", "900", "100", 0, "QOQ_ONLY"));
        var pat = List.of(obs(Q3, Q2, "90", "60", "30", 0, "QOQ_ONLY"));
        var staleMargin = obs(Q2, Q1, "10.0", "8.0", "2.0", 0, "QOQ_ONLY"); // wrong period

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, revenue, pat, staleMargin);

        assertThat(result.primaryState()).isNotEqualTo(FinancialInflectionState.OPERATING_LEVERAGE_INFLECTION);
    }

    @Test
    void earningsInflectionConvergenceRequiresAllThreeComponentsAndAlignedPeriods() {
        var revenue = List.of(
            obs(Q1, null, "110", "100", null, 0, null),
            obs(Q2, Q1, "126.5", null, null, 0, null),
            obs(Q3, Q2, "151.8", null, null, 0, null)
        );
        var pat = List.of(
            obs(Q1, null, "55", "50", null, 0, null),
            obs(Q2, Q1, "63.25", null, null, 0, null),
            obs(Q3, Q2, "75.9", null, null, 0, null)
        );
        var margin = obs(Q3, Q2, "12.50", "11.00", "1.50", 3, "QOQ_ONLY");

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, revenue, pat, margin);

        assertThat(result.primaryState()).isEqualTo(FinancialInflectionState.EARNINGS_INFLECTION_CONVERGENCE);
        assertThat(result.drivingMetric()).isEqualTo(FinancialMetric.PAT);
        assertThat(result.persistence()).isEqualTo(2); // min(revenueAccel=2, patAccel=2, marginPersistence=3)
    }

    @Test
    void bankOnlyPatCanStillAccelerateWithNoRevenueOrMarginData() {
        var pat = List.of(
            obs(Q1, null, "110", "100", null, 0, null),
            obs(Q2, Q1, "126.5", null, null, 0, null),
            obs(Q3, Q2, "151.8", null, null, 0, null)
        );

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, List.of(), pat, null);

        assertThat(result.primaryState()).isEqualTo(FinancialInflectionState.PAT_ACCELERATION);
        assertThat(result.drivingMetric()).isEqualTo(FinancialMetric.PAT);
    }

    @Test
    void noClearSignalFallsBackToAveragedEvidenceConfidence() {
        var revenue = List.of(obs(Q3, Q2, "1000", "950", "50", 0, "QOQ_ONLY", 90.0));
        var pat = List.of(obs(Q3, Q2, "90", "92", "-2", 0, "QOQ_ONLY", 40.0));

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, revenue, pat, null);

        assertThat(result.primaryState()).isEqualTo(FinancialInflectionState.NO_CLEAR_SIGNAL);
        assertThat(result.drivingMetric()).isNull();
        assertThat(result.asOfDate()).isEqualTo(Q3);
        assertThat(result.confidence()).isEqualTo(65.0);
    }

    private static FinancialEvidenceObservation obs(
        LocalDate periodEnd, LocalDate priorPeriodEnd, String value, String priorValue, String change, int persistence, String comparatorUsed
    ) {
        return obs(periodEnd, priorPeriodEnd, value, priorValue, change, persistence, comparatorUsed, 90.0);
    }

    private static FinancialEvidenceObservation obs(
        LocalDate periodEnd, LocalDate priorPeriodEnd, String value, String priorValue, String change, int persistence, String comparatorUsed, double confidence
    ) {
        return new FinancialEvidenceObservation(
            FinancialMetric.REVENUE, INSTRUMENT_ID, SYMBOL, periodEnd, priorPeriodEnd,
            new BigDecimal(value), priorValue == null ? null : new BigDecimal(priorValue), change == null ? null : new BigDecimal(change),
            null, persistence, confidence, comparatorUsed
        );
    }
}
