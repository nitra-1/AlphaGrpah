package com.alphagraph.corporate.transformation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapitalAllocationEvidenceObservationTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 1, 1);

    @Test
    void acceptsEnteredMinusExitedEqualToChange() {
        var observation = new CapitalAllocationEvidenceObservation(
            CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D, INSTRUMENT_ID, SYMBOL, AS_OF_DATE, AS_OF_DATE.minusDays(1),
            1, 1, 0, 1, 1, 0, 0, 90.0, 180
        );

        assertThat(observation.change()).isEqualTo(observation.enteredEventCount() - observation.exitedEventCount());
    }

    @Test
    void rejectsChangeInconsistentWithEnteredMinusExited() {
        assertThatThrownBy(() -> new CapitalAllocationEvidenceObservation(
            CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D, INSTRUMENT_ID, SYMBOL, AS_OF_DATE, AS_OF_DATE.minusDays(1),
            1, 0, 1, 0, 0, 1, 0, 90.0, 180 // change=1 but entered(0) - exited(0) = 0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeEnteredEventCount() {
        assertThatThrownBy(() -> new CapitalAllocationEvidenceObservation(
            CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D, INSTRUMENT_ID, SYMBOL, AS_OF_DATE, AS_OF_DATE.minusDays(1),
            1, 2, -1, -1, 0, -1, 0, 90.0, 180
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeExitedEventCount() {
        assertThatThrownBy(() -> new CapitalAllocationEvidenceObservation(
            CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D, INSTRUMENT_ID, SYMBOL, AS_OF_DATE, AS_OF_DATE.minusDays(1),
            1, 0, 1, 0, -1, 1, 0, 90.0, 180 // entered(0) - exited(-1) = 1 satisfies change, but exited < 0 is still invalid
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
