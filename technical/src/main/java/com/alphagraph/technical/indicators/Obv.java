package com.alphagraph.technical.indicators;

import java.util.List;

/** On-Balance Volume: cumulative volume added on up-closes, subtracted on down-closes. */
public final class Obv {

    private Obv() {
    }

    public static long of(List<Double> closesAscending, List<Long> volumesAscending) {
        long obv = 0;
        for (int i = 1; i < closesAscending.size(); i++) {
            if (closesAscending.get(i) > closesAscending.get(i - 1)) {
                obv += volumesAscending.get(i);
            } else if (closesAscending.get(i) < closesAscending.get(i - 1)) {
                obv -= volumesAscending.get(i);
            }
        }
        return obv;
    }

    /**
     * OBV slope proxy: OBV computed over the full series minus OBV computed over the series with
     * the most recent {@code lookback} bars removed. Positive means OBV has risen recently.
     */
    public static double slope(List<Double> closesAscending, List<Long> volumesAscending, int lookback) {
        int n = closesAscending.size();
        if (n <= lookback) {
            return 0.0;
        }
        long obvNow = of(closesAscending, volumesAscending);
        long obvBefore = of(closesAscending.subList(0, n - lookback), volumesAscending.subList(0, n - lookback));
        return obvNow - obvBefore;
    }
}
