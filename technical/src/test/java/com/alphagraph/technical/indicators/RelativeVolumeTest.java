package com.alphagraph.technical.indicators;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class RelativeVolumeTest {

    @Test
    void doubleTheAverageVolumeProducesRelativeVolumeOfTwo() {
        List<Long> volumes = List.of(100L, 100L, 100L, 100L, 100L, 200L);

        OptionalDouble relativeVolume = RelativeVolume.of(volumes, 5);

        assertThat(relativeVolume).hasValue(2.0);
    }

    @Test
    void insufficientDataIsEmpty() {
        assertThat(RelativeVolume.of(List.of(100L, 200L), 5)).isEmpty();
    }
}
