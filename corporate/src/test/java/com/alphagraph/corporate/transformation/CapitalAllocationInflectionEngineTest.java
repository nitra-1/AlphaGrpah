package com.alphagraph.corporate.transformation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CapitalAllocationInflectionEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 9, 19);

    private final CapitalAllocationInflectionEngine engine = new CapitalAllocationInflectionEngine();

    @Test
    void buybackActivityFiresWhenBuybackCountIsPositiveAndEquityRaiseIsZero() {
        var buyback = obs(CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D, 2, 1, 30, 90.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, AS_OF_DATE, buyback, null);

        assertThat(result.primaryState()).isEqualTo(CapitalAllocationInflectionState.BUYBACK_ACTIVITY);
        assertThat(result.drivingMetric()).isEqualTo(CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D);
        assertThat(result.level()).isEqualTo(2);
        assertThat(result.change()).isEqualTo(1);
        assertThat(result.persistence()).isEqualTo(30);
        assertThat(result.confidence()).isEqualTo(85.0); // 75 + min(10, 2*30)
    }

    @Test
    void buybackActivityDoesNotFireWhenCountIsZero() {
        var buyback = obs(CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D, 0, 0, 0, 90.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, AS_OF_DATE, buyback, null);

        assertThat(result.primaryState()).isEqualTo(CapitalAllocationInflectionState.NO_CLEAR_SIGNAL);
    }

    @Test
    void equityRaiseActivityFiresWhenEquityRaiseCountIsPositiveAndBuybackIsZero() {
        var equityRaise = obs(CapitalAllocationMetric.EQUITY_RAISE_EVENT_COUNT_180D, 1, 1, 10, 90.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, AS_OF_DATE, null, equityRaise);

        assertThat(result.primaryState()).isEqualTo(CapitalAllocationInflectionState.EQUITY_RAISE_ACTIVITY);
        assertThat(result.drivingMetric()).isEqualTo(CapitalAllocationMetric.EQUITY_RAISE_EVENT_COUNT_180D);
        assertThat(result.level()).isEqualTo(1);
        assertThat(result.persistence()).isEqualTo(10);
    }

    @Test
    void mixedCapitalAllocationActivityWinsPriorityWhenBothCountsArePositive() {
        var buyback = obs(CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D, 3, 1, 20, 90.0);
        var equityRaise = obs(CapitalAllocationMetric.EQUITY_RAISE_EVENT_COUNT_180D, 1, 1, 5, 90.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, AS_OF_DATE, buyback, equityRaise);

        assertThat(result.primaryState()).isEqualTo(CapitalAllocationInflectionState.MIXED_CAPITAL_ALLOCATION_ACTIVITY);
        // Both reason codes recorded regardless of which side is representative.
        assertThat(result.reasons()).extracting("code").containsExactlyInAnyOrder(
            "BUYBACK_EVENT_COUNT_180D_NONZERO", "EQUITY_RAISE_EVENT_COUNT_180D_NONZERO"
        );
        // Magnitude tie-break: buyback (3) is larger than equity raise (1).
        assertThat(result.drivingMetric()).isEqualTo(CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D);
        assertThat(result.level()).isEqualTo(3);
        // persistence = min(20, 5).
        assertThat(result.persistence()).isEqualTo(5);
    }

    @Test
    void noClearSignalWhenBothCountsAreZero_theRealCurrentStateOfEveryTrackedInstrument() {
        var buyback = obs(CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D, 0, 0, 0, 90.0);
        var equityRaise = obs(CapitalAllocationMetric.EQUITY_RAISE_EVENT_COUNT_180D, 0, 0, 0, 90.0);

        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, AS_OF_DATE, buyback, equityRaise);

        assertThat(result.primaryState()).isEqualTo(CapitalAllocationInflectionState.NO_CLEAR_SIGNAL);
        assertThat(result.drivingMetric()).isNull();
        assertThat(result.confidence()).isEqualTo(90.0); // average of the two Stage 1 confidences
    }

    @Test
    void noClearSignalWhenBothEvidenceRowsAreMissingEntirely() {
        var result = engine.calculate(INSTRUMENT_ID, SYMBOL, AS_OF_DATE, null, null);

        assertThat(result.primaryState()).isEqualTo(CapitalAllocationInflectionState.NO_CLEAR_SIGNAL);
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    private static CapitalAllocationEvidenceObservation obs(CapitalAllocationMetric metric, int value, int priorValue, int persistenceDays, double confidence) {
        return new CapitalAllocationEvidenceObservation(
            metric, INSTRUMENT_ID, SYMBOL, AS_OF_DATE, AS_OF_DATE.minusDays(1),
            value, priorValue, value - priorValue, value - priorValue, persistenceDays, confidence, 180
        );
    }
}
