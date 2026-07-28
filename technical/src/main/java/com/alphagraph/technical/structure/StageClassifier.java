package com.alphagraph.technical.structure;

import java.util.List;
import java.util.Optional;

/**
 * Simplified Weinstein-style stage classification (1 Basing, 2 Advancing, 3 Topping, 4 Declining)
 * from the weekly SMA's recent slope and the latest weekly close's position relative to it.
 * Returns empty when there isn't enough weekly history to compute a slope (needs the SMA series
 * itself, not just one reading) - no fallback approximation is used, since a stage call made up
 * from insufficient data would be worse than admitting it isn't available yet.
 */
public final class StageClassifier {

    private static final int SLOPE_LOOKBACK_WEEKS = 4;

    private StageClassifier() {
    }

    public static Optional<Integer> classify(List<Double> weeklyClosesAscending, int smaPeriod) {
        if (weeklyClosesAscending.size() < smaPeriod + SLOPE_LOOKBACK_WEEKS) {
            return Optional.empty();
        }

        double currentSma = average(weeklyClosesAscending, weeklyClosesAscending.size(), smaPeriod);
        double priorSma = average(weeklyClosesAscending, weeklyClosesAscending.size() - SLOPE_LOOKBACK_WEEKS, smaPeriod);
        double latestClose = weeklyClosesAscending.get(weeklyClosesAscending.size() - 1);

        boolean smaRising = currentSma > priorSma;
        boolean priceAboveSma = latestClose > currentSma;

        if (priceAboveSma && smaRising) {
            return Optional.of(2);
        }
        if (!priceAboveSma && !smaRising) {
            return Optional.of(4);
        }
        if (priceAboveSma) {
            return Optional.of(3);
        }
        return Optional.of(1);
    }

    private static double average(List<Double> values, int uptoExclusive, int period) {
        List<Double> window = values.subList(uptoExclusive - period, uptoExclusive);
        double sum = 0;
        for (double v : window) {
            sum += v;
        }
        return sum / period;
    }
}
