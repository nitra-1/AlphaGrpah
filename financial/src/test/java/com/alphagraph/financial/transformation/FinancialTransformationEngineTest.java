package com.alphagraph.financial.transformation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialTransformationEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";

    private final FinancialTransformationEngine engine = new FinancialTransformationEngine();

    @Test
    void emptyHistoryProducesNoEvidence() {
        assertThat(engine.calculate(List.of())).isEmpty();
    }

    @Test
    void singlePeriodIsFirstObservationWithNullPrior() {
        var periods = List.of(period(LocalDate.of(2024, 12, 31), "12826000", "872100", null));

        var evidence = engine.calculate(periods);

        assertThat(evidence).hasSize(2); // REVENUE + PAT, OPERATING_MARGIN/INTEREST_EXPENSE are null
        for (var observation : evidence) {
            assertThat(observation.priorPeriodEnd()).isNull();
            assertThat(observation.comparatorUsed()).isNull();
            assertThat(observation.confidence()).isEqualTo(40.0);
        }
    }

    @Test
    void fiveQuartersPrefersYoyOverQoqForTheCurrentPeriod() {
        // index 0 (oldest) is exactly 4 quarters (12 months) before index 4 (current, most recent).
        var periods = List.of(
            period(LocalDate.of(2023, 12, 31), "13057900", "992400", null),
            period(LocalDate.of(2024, 3, 31), "15101400", "1128300", null),
            period(LocalDate.of(2024, 6, 30), "13433100", "761100", null),
            period(LocalDate.of(2024, 9, 30), "13405400", "771300", null),
            period(LocalDate.of(2024, 12, 31), "12826000", "872100", null)
        );

        var evidence = engine.calculate(periods);

        var revenue = evidence.stream().filter(o -> o.metric() == FinancialMetric.REVENUE).findFirst().orElseThrow();
        assertThat(revenue.comparatorUsed()).isEqualTo("YOY");
        assertThat(revenue.priorPeriodEnd()).isEqualTo(LocalDate.of(2023, 12, 31));
        assertThat(revenue.priorValue()).isEqualByComparingTo("13057900");
        assertThat(revenue.change()).isEqualByComparingTo("-231900");
    }

    @Test
    void fourQuartersFallsBackToQoqWhenNoYoyPointExists() {
        var periods = List.of(
            period(LocalDate.of(2024, 3, 31), "15101400", "1128300", null),
            period(LocalDate.of(2024, 6, 30), "13433100", "761100", null),
            period(LocalDate.of(2024, 9, 30), "13405400", "771300", null),
            period(LocalDate.of(2024, 12, 31), "12826000", "872100", null)
        );

        var evidence = engine.calculate(periods);

        var revenue = evidence.stream().filter(o -> o.metric() == FinancialMetric.REVENUE).findFirst().orElseThrow();
        assertThat(revenue.comparatorUsed()).isEqualTo("QOQ_ONLY");
        assertThat(revenue.priorPeriodEnd()).isEqualTo(LocalDate.of(2024, 9, 30));
    }

    @Test
    void operatingMarginAndInterestExpenseAreSkippedWhenNull() {
        var periods = List.of(period(LocalDate.of(2024, 12, 31), "12826000", "872100", null));

        var evidence = engine.calculate(periods);

        assertThat(evidence).extracting(FinancialEvidenceObservation::metric)
            .containsExactlyInAnyOrder(FinancialMetric.REVENUE, FinancialMetric.PAT);
    }

    private static FinancialResultsPeriod period(LocalDate periodEnd, String revenue, String pat, String operatingMargin) {
        return new FinancialResultsPeriod(INSTRUMENT_ID, SYMBOL, periodEnd, new BigDecimal(revenue), new BigDecimal(pat), operatingMargin == null ? null : new BigDecimal(operatingMargin), null);
    }
}
