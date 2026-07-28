package com.alphagraph.technical.indicators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Package-private EMA helper shared by {@link Macd}. Returns a same-length series so callers can
 * align two EMAs of different periods by index; entries before the seed SMA at index
 * {@code period - 1} are {@link Double#NaN} (not yet available).
 */
final class Ema {

    private Ema() {
    }

    static List<Double> series(List<Double> valuesAscending, int period) {
        int n = valuesAscending.size();
        List<Double> result = new ArrayList<>(Collections.nCopies(n, Double.NaN));
        if (n < period) {
            return result;
        }

        double multiplier = 2.0 / (period + 1);
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += valuesAscending.get(i);
        }
        double ema = sum / period;
        result.set(period - 1, ema);

        for (int i = period; i < n; i++) {
            ema = (valuesAscending.get(i) - ema) * multiplier + ema;
            result.set(i, ema);
        }
        return result;
    }
}
