package com.alphagraph.technical.indicators;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RsiTest {

    @Test
    void allGainsProducesMaximumRsi() {
        List<Double> closes = List.of(10.0, 11.0, 12.0, 13.0, 14.0, 15.0);

        OptionalDouble rsi = Rsi.of(closes, 5);

        assertThat(rsi).hasValue(100.0);
    }

    @Test
    void allLossesProducesMinimumRsi() {
        List<Double> closes = List.of(15.0, 14.0, 13.0, 12.0, 11.0, 10.0);

        OptionalDouble rsi = Rsi.of(closes, 5);

        assertThat(rsi).hasValue(0.0);
    }

    @Test
    void equalGainsAndLossesProducesRsiNear50() {
        // Alternating +1/-1 changes converge (after enough bars for Wilder's smoothing to shed
        // the initial transient) to a steady 2-cycle in [48.15, 51.86] for period 14 - never
        // exactly 50 since the smoothing itself alternates phase with the input, but always
        // within a couple of points of it. A short sequence would still be in the transient
        // (its first window isn't gain/loss-balanced) and land well outside that band, which is
        // exactly the failure this test caught before increasing the sequence length.
        List<Double> closes = new ArrayList<>();
        double price = 100.0;
        closes.add(price);
        for (int i = 0; i < 60; i++) {
            price += (i % 2 == 0) ? 1.0 : -1.0;
            closes.add(price);
        }

        OptionalDouble rsi = Rsi.of(closes, 14);

        assertThat(rsi.getAsDouble()).isCloseTo(50.0, within(2.0));
    }

    @Test
    void insufficientDataIsEmpty() {
        assertThat(Rsi.of(List.of(10.0, 11.0), 14)).isEmpty();
    }
}
