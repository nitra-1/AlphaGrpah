package com.alphagraph.financial.transformation;

import com.alphagraph.common.inflection.VelocityBand;

import java.math.BigDecimal;

/**
 * Hardcoded Java threshold constants, never a DB rule - same convention
 * {@code ownership.transformation.VelocityBanding}/{@code market.transformation.MarketVelocityBanding}
 * already established. Only one band type: every quantity Stage 2 bands for this family is already
 * a growth rate or an acceleration in percentage points (never a raw currency delta) - see
 * {@code FinancialInflectionEngine}'s own javadoc for why.
 */
final class FinancialVelocityBanding {

    private static final BigDecimal STRONG_THRESHOLD = new BigDecimal("2.0");
    private static final BigDecimal MODERATE_THRESHOLD = new BigDecimal("0.5");

    private FinancialVelocityBanding() {
    }

    static VelocityBand bandPercentagePoint(BigDecimal value) {
        int sign = value.signum();
        if (sign < 0) {
            return VelocityBand.NEGATIVE;
        }
        if (sign == 0) {
            return VelocityBand.FLAT;
        }
        if (value.compareTo(STRONG_THRESHOLD) >= 0) {
            return VelocityBand.STRONG;
        }
        if (value.compareTo(MODERATE_THRESHOLD) >= 0) {
            return VelocityBand.MODERATE;
        }
        return VelocityBand.WEAK;
    }
}
