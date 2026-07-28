package com.alphagraph.technical.indicators;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class AdxTest {

    @Test
    void steadyDirectionalMoveProducesHighAdx() {
        // Every bar's high and low ratchet up by 1 with no overlap - a textbook strong,
        // unidirectional trend, which should produce a high (>40) ADX.
        List<Double> highs = new ArrayList<>();
        List<Double> lows = new ArrayList<>();
        List<Double> closes = new ArrayList<>();
        double level = 100.0;
        for (int i = 0; i < 40; i++) {
            highs.add(level + 2);
            lows.add(level);
            closes.add(level + 1);
            level += 2;
        }

        OptionalDouble adx = Adx.of(highs, lows, closes, 14);

        assertThat(adx).isPresent();
        assertThat(adx.getAsDouble()).isGreaterThan(40.0);
    }

    @Test
    void insufficientDataIsEmpty() {
        List<Double> highs = List.of(101.0, 102.0);
        List<Double> lows = List.of(99.0, 100.0);
        List<Double> closes = List.of(100.0, 101.0);

        assertThat(Adx.of(highs, lows, closes, 14)).isEmpty();
    }
}
