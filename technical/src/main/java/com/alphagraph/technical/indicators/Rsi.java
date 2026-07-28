package com.alphagraph.technical.indicators;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Wilder's RSI: the first average gain/loss is a simple mean over {@code period} price changes,
 * then Wilder-smoothed for every change after that. Needs at least {@code period + 1} closes.
 */
public final class Rsi {

    private Rsi() {
    }

    public static OptionalDouble of(List<Double> closesAscending, int period) {
        if (closesAscending.size() < period + 1) {
            return OptionalDouble.empty();
        }

        double avgGain = 0;
        double avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double change = closesAscending.get(i) - closesAscending.get(i - 1);
            if (change > 0) {
                avgGain += change;
            } else {
                avgLoss += -change;
            }
        }
        avgGain /= period;
        avgLoss /= period;

        for (int i = period + 1; i < closesAscending.size(); i++) {
            double change = closesAscending.get(i) - closesAscending.get(i - 1);
            double gain = Math.max(change, 0);
            double loss = Math.max(-change, 0);
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        if (avgLoss == 0) {
            return OptionalDouble.of(100.0);
        }
        double rs = avgGain / avgLoss;
        return OptionalDouble.of(100.0 - (100.0 / (1.0 + rs)));
    }
}
