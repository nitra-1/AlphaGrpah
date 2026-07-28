package com.alphagraph.technical.indicators;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObvTest {

    @Test
    void upCloseAddsVolumeDownCloseSubtractsFlatIsUnchanged() {
        List<Double> closes = List.of(100.0, 101.0, 99.0, 99.0);
        List<Long> volumes = List.of(1000L, 500L, 300L, 200L);

        long obv = Obv.of(closes, volumes);

        // day2: up close -> +500; day3: down close -> -300; day4: flat -> +0
        assertThat(obv).isEqualTo(500 - 300);
    }

    @Test
    void slopeIsPositiveWhenObvHasBeenRising() {
        List<Double> closes = List.of(100.0, 101.0, 102.0, 103.0, 104.0);
        List<Long> volumes = List.of(1000L, 1000L, 1000L, 1000L, 1000L);

        double slope = Obv.slope(closes, volumes, 2);

        assertThat(slope).isPositive();
    }
}
