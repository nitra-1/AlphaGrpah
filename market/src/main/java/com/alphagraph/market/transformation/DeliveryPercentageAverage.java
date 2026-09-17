package com.alphagraph.market.transformation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/** Simple moving average of delivery percentage over the trailing {@code lookback} sessions, including the most recent one. */
final class DeliveryPercentageAverage {

    private DeliveryPercentageAverage() {
    }

    static Optional<BigDecimal> of(List<BigDecimal> deliveryPercentagesAscending, int lookback) {
        int n = deliveryPercentagesAscending.size();
        if (n < lookback) {
            return Optional.empty();
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = n - lookback; i < n; i++) {
            BigDecimal value = deliveryPercentagesAscending.get(i);
            if (value == null) {
                return Optional.empty();
            }
            sum = sum.add(value);
        }
        return Optional.of(sum.divide(BigDecimal.valueOf(lookback), 4, RoundingMode.HALF_UP));
    }
}
