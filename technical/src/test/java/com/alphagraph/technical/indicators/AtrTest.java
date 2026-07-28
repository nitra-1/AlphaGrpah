package com.alphagraph.technical.indicators;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class AtrTest {

    @Test
    void constantRangeProducesThatRangeAsAtr() {
        // Every bar has high=101, low=99, close=100 - true range is always 2, regardless of
        // the prevClose terms, so ATR should converge to exactly 2.
        List<Double> highs = new ArrayList<>();
        List<Double> lows = new ArrayList<>();
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            highs.add(101.0);
            lows.add(99.0);
            closes.add(100.0);
        }

        OptionalDouble atr = Atr.of(highs, lows, closes, 14);

        assertThat(atr).hasValue(2.0);
    }

    @Test
    void insufficientDataIsEmpty() {
        List<Double> highs = List.of(101.0, 102.0);
        List<Double> lows = List.of(99.0, 100.0);
        List<Double> closes = List.of(100.0, 101.0);

        assertThat(Atr.of(highs, lows, closes, 14)).isEmpty();
    }
}
