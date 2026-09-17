package com.alphagraph.market.transformation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Most recent day's volume divided by the average of the preceding {@code lookback} days' volume.
 * Same algorithm as {@code technical.indicators.RelativeVolume}, reimplemented locally rather than
 * reused - {@code market} cannot depend on {@code technical} (domain modules never depend on each
 * other directly, docs/001_System_Architecture.md §4 Rule 3).
 */
final class RelativeVolumeCalculator {

    private RelativeVolumeCalculator() {
    }

    static Optional<BigDecimal> of(List<Long> volumesAscending, int lookback) {
        int n = volumesAscending.size();
        if (n < lookback + 1) {
            return Optional.empty();
        }

        long sum = 0;
        for (int i = n - 1 - lookback; i < n - 1; i++) {
            sum += volumesAscending.get(i);
        }
        if (sum == 0) {
            return Optional.empty();
        }
        BigDecimal avg = BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(lookback), 8, RoundingMode.HALF_UP);
        return Optional.of(BigDecimal.valueOf(volumesAscending.get(n - 1)).divide(avg, 4, RoundingMode.HALF_UP));
    }
}
