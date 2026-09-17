package com.alphagraph.intelligence.sectorcontext;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SectorContextEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";

    private final SectorContextEngine engine = new SectorContextEngine();

    @Test
    void emptySeriesProducesNoObservation() {
        assertThat(engine.calculate(SectorContextMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY, INSTRUMENT_ID, SYMBOL, List.of())).isEmpty();
    }

    @Test
    void singlePointIsFirstObservationWithNullPriorAndLowerConfidence() {
        var series = List.of(new DatedValue(LocalDate.of(2026, 9, 11), new BigDecimal("1.50")));

        var observation = engine.calculate(SectorContextMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY, INSTRUMENT_ID, SYMBOL, series).orElseThrow();

        assertThat(observation.priorAsOfDate()).isNull();
        assertThat(observation.priorValue()).isNull();
        assertThat(observation.change()).isNull();
        assertThat(observation.confidence()).isEqualTo(40.0);
        assertThat(observation.value()).isEqualByComparingTo("1.50");
    }

    @Test
    void twoPointsProduceARealChange() {
        var series = List.of(
            new DatedValue(LocalDate.of(2026, 9, 10), new BigDecimal("1.00")),
            new DatedValue(LocalDate.of(2026, 9, 11), new BigDecimal("2.50"))
        );

        var observation = engine.calculate(SectorContextMetric.INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR, INSTRUMENT_ID, SYMBOL, series).orElseThrow();

        assertThat(observation.priorAsOfDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(observation.priorValue()).isEqualByComparingTo("1.00");
        assertThat(observation.change()).isEqualByComparingTo("1.50");
        assertThat(observation.confidence()).isEqualTo(90.0);
    }

    @Test
    void persistenceCountsConsecutiveSameSignTransitions() {
        var series = List.of(
            new DatedValue(LocalDate.of(2026, 9, 1), new BigDecimal("1.00")),
            new DatedValue(LocalDate.of(2026, 9, 2), new BigDecimal("2.00")), // +1
            new DatedValue(LocalDate.of(2026, 9, 3), new BigDecimal("3.00")), // +1
            new DatedValue(LocalDate.of(2026, 9, 4), new BigDecimal("4.50"))  // +1.5 (current)
        );

        var observation = engine.calculate(SectorContextMetric.SECTOR_RELATIVE_STRENGTH, INSTRUMENT_ID, SYMBOL, series).orElseThrow();

        // All 3 day-over-day transitions in this 4-point series are positive, so persistence
        // counts all 3, including today's own transition.
        assertThat(observation.persistenceDays()).isEqualTo(3);
    }
}
