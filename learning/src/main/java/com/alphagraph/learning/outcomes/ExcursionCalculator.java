package com.alphagraph.learning.outcomes;

import com.alphagraph.intelligence.priceadjustment.AdjustedDailyPrice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Max Favorable/Adverse Excursion - the best and worst the adjusted price path got during a
 * horizon, not just where it ended up. Uses {@link AdjustedDailyPrice#adjustedHigh()}/
 * {@link AdjustedDailyPrice#adjustedLow()} (the same {@code cumulativeFactor} already applied to
 * close, extended to the intraday range), over the trading days strictly after the decision date
 * through the horizon's outcome day - the same index window {@code ForwardOutcomeEngine} already
 * uses for the close-to-close return.
 */
@Component
class ExcursionCalculator {

    private static final int RETURN_SCALE = 2;

    ExcursionResult compute(List<AdjustedDailyPrice> sortedHistory, int referenceIndex, int horizonDays, BigDecimal referencePrice) {
        int endIndex = referenceIndex + horizonDays;
        BigDecimal maxHigh = null;
        BigDecimal minLow = null;
        for (int i = referenceIndex + 1; i <= endIndex && i < sortedHistory.size(); i++) {
            AdjustedDailyPrice day = sortedHistory.get(i);
            if (maxHigh == null || day.adjustedHigh().compareTo(maxHigh) > 0) {
                maxHigh = day.adjustedHigh();
            }
            if (minLow == null || day.adjustedLow().compareTo(minLow) < 0) {
                minLow = day.adjustedLow();
            }
        }
        if (maxHigh == null || minLow == null) {
            return null;
        }

        BigDecimal mfe = maxHigh.subtract(referencePrice)
            .divide(referencePrice, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(RETURN_SCALE, RoundingMode.HALF_UP);
        BigDecimal mae = minLow.subtract(referencePrice)
            .divide(referencePrice, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(RETURN_SCALE, RoundingMode.HALF_UP);

        return new ExcursionResult(mfe, mae);
    }

    record ExcursionResult(BigDecimal mfePercentage, BigDecimal maePercentage) {
    }
}
