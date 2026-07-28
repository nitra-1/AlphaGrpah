package com.alphagraph.technical.indicators;

import java.util.List;
import java.util.OptionalDouble;

/** Simple moving average of the most recent {@code period} values. */
public final class Sma {

    private Sma() {
    }

    public static OptionalDouble of(List<Double> valuesAscending, int period) {
        if (valuesAscending.size() < period) {
            return OptionalDouble.empty();
        }
        List<Double> window = valuesAscending.subList(valuesAscending.size() - period, valuesAscending.size());
        double sum = 0;
        for (double v : window) {
            sum += v;
        }
        return OptionalDouble.of(sum / period);
    }
}
