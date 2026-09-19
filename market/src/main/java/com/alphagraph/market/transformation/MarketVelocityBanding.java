package com.alphagraph.market.transformation;

import com.alphagraph.common.inflection.VelocityBand;

import java.math.BigDecimal;

/**
 * Hardcoded Java threshold constants, never a DB rule - same convention
 * {@code ownership.transformation.VelocityBanding} already established, see its own javadoc for
 * why. Market uses two of the three band types docs/007_Stage2_Inflection_Specification.md §15.4
 * defines: percentage-point ({@code DELIVERY_PERCENTAGE_20D_AVG}, {@code PRICE_RETURN_20D}) and
 * ratio ({@code RELATIVE_VOLUME}).
 */
final class MarketVelocityBanding {

    private static final BigDecimal PP_STRONG_THRESHOLD = new BigDecimal("2.0");
    private static final BigDecimal PP_MODERATE_THRESHOLD = new BigDecimal("0.5");

    private static final BigDecimal RATIO_STRONG_THRESHOLD = new BigDecimal("0.5");
    private static final BigDecimal RATIO_MODERATE_THRESHOLD = new BigDecimal("0.2");

    private MarketVelocityBanding() {
    }

    static VelocityBand bandPercentagePoint(BigDecimal velocity) {
        return band(velocity, PP_STRONG_THRESHOLD, PP_MODERATE_THRESHOLD);
    }

    static VelocityBand bandRatio(BigDecimal velocity) {
        return band(velocity, RATIO_STRONG_THRESHOLD, RATIO_MODERATE_THRESHOLD);
    }

    private static VelocityBand band(BigDecimal velocity, BigDecimal strongThreshold, BigDecimal moderateThreshold) {
        int sign = velocity.signum();
        if (sign < 0) {
            return VelocityBand.NEGATIVE;
        }
        if (sign == 0) {
            return VelocityBand.FLAT;
        }
        if (velocity.compareTo(strongThreshold) >= 0) {
            return VelocityBand.STRONG;
        }
        if (velocity.compareTo(moderateThreshold) >= 0) {
            return VelocityBand.MODERATE;
        }
        return VelocityBand.WEAK;
    }

    static VelocityBand bandFor(MarketMetric metric, BigDecimal velocity) {
        return metric == MarketMetric.RELATIVE_VOLUME ? bandRatio(velocity) : bandPercentagePoint(velocity);
    }
}
