package com.alphagraph.technical.indicators;

import java.util.List;
import java.util.OptionalDouble;

/** Most recent day's volume divided by the average of the preceding {@code lookback} days' volume. */
public final class RelativeVolume {

    private RelativeVolume() {
    }

    public static OptionalDouble of(List<Long> volumesAscending, int lookback) {
        int n = volumesAscending.size();
        if (n < lookback + 1) {
            return OptionalDouble.empty();
        }

        long sum = 0;
        for (int i = n - 1 - lookback; i < n - 1; i++) {
            sum += volumesAscending.get(i);
        }
        double avg = (double) sum / lookback;
        if (avg == 0) {
            return OptionalDouble.empty();
        }

        return OptionalDouble.of(volumesAscending.get(n - 1) / avg);
    }
}
