package com.alphagraph.technical.indicators;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MacdTest {

    @Test
    void steadyUptrendProducesPositiveMacdLine() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            closes.add(100.0 + i);
        }

        Optional<Macd.MacdResult> result = Macd.of(closes, 12, 26, 9);

        // A steady uptrend means the fast EMA leads (sits above) the slow EMA - the MACD line
        // should be positive. The histogram isn't asserted here: on a *steady* (constant-slope)
        // trend the line itself converges toward a constant, so by bar 60 the signal line (an
        // EMA of the line) has largely caught up and the histogram is near zero - its sign at
        // that point is a coin flip on floating-point noise, not a property of the trend.
        assertThat(result).isPresent();
        assertThat(result.get().line()).isPositive();
    }

    @Test
    void newlyStartedUptrendProducesPositiveHistogram() {
        // 40 flat bars, then a trend that just started - the MACD line is still rising (the
        // signal line, an EMA of it, necessarily lags below a rising line), so the histogram
        // (line - signal) is unambiguously positive right after the trend begins.
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            closes.add(100.0);
        }
        for (int i = 0; i < 20; i++) {
            closes.add(100.0 + i);
        }

        Optional<Macd.MacdResult> result = Macd.of(closes, 12, 26, 9);

        assertThat(result).isPresent();
        assertThat(result.get().histogram()).isPositive();
    }

    @Test
    void flatPricesProduceZeroMacd() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            closes.add(100.0);
        }

        Optional<Macd.MacdResult> result = Macd.of(closes, 12, 26, 9);

        assertThat(result).isPresent();
        assertThat(result.get().line()).isEqualTo(0.0);
        assertThat(result.get().histogram()).isEqualTo(0.0);
    }

    @Test
    void insufficientDataIsEmpty() {
        List<Double> closes = List.of(1.0, 2.0, 3.0);

        assertThat(Macd.of(closes, 12, 26, 9)).isEmpty();
    }
}
