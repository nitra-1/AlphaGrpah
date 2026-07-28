package com.alphagraph.technical.indicators;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Wilder's ATR: true range (max of high-low, |high-prevClose|, |low-prevClose|), simple-averaged
 * over the first {@code period} bars, then Wilder-smoothed thereafter.
 */
public final class Atr {

    private Atr() {
    }

    public static OptionalDouble of(List<Double> highs, List<Double> lows, List<Double> closes, int period) {
        int n = closes.size();
        if (n < period + 1) {
            return OptionalDouble.empty();
        }

        double[] tr = new double[n];
        tr[0] = highs.get(0) - lows.get(0);
        for (int i = 1; i < n; i++) {
            double hl = highs.get(i) - lows.get(i);
            double hc = Math.abs(highs.get(i) - closes.get(i - 1));
            double lc = Math.abs(lows.get(i) - closes.get(i - 1));
            tr[i] = Math.max(hl, Math.max(hc, lc));
        }

        double atr = 0;
        for (int i = 1; i <= period; i++) {
            atr += tr[i];
        }
        atr /= period;

        for (int i = period + 1; i < n; i++) {
            atr = (atr * (period - 1) + tr[i]) / period;
        }

        return OptionalDouble.of(atr);
    }
}
