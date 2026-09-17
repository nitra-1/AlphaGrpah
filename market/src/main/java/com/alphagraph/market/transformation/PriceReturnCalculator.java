package com.alphagraph.market.transformation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/** Percentage return of the most recent close vs. the close {@code lookback} trading sessions ago. */
final class PriceReturnCalculator {

    private PriceReturnCalculator() {
    }

    static Optional<BigDecimal> of(List<BigDecimal> closesAscending, int lookback) {
        int n = closesAscending.size();
        if (n < lookback + 1) {
            return Optional.empty();
        }

        BigDecimal past = closesAscending.get(n - 1 - lookback);
        BigDecimal current = closesAscending.get(n - 1);
        if (past == null || current == null || past.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(
            current.subtract(past).divide(past, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP)
        );
    }
}
