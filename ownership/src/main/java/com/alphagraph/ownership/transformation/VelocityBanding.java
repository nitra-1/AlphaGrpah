package com.alphagraph.ownership.transformation;

import com.alphagraph.common.inflection.VelocityBand;

import java.math.BigDecimal;

/**
 * Hardcoded Java threshold constants, never a DB rule - see {@link VelocityBand}'s own javadoc for
 * why. Percentage-point bands (docs/007_Stage2_Inflection_Specification.md §15.4 - all 9 Ownership
 * metrics are this type), applied to {@code velocityPpPerQuarter}, not {@code changePp}.
 */
final class VelocityBanding {

    private static final BigDecimal STRONG_THRESHOLD = new BigDecimal("2.0");
    private static final BigDecimal MODERATE_THRESHOLD = new BigDecimal("0.5");

    private VelocityBanding() {
    }

    static VelocityBand band(BigDecimal velocityPpPerQuarter) {
        int sign = velocityPpPerQuarter.signum();
        if (sign < 0) {
            return VelocityBand.NEGATIVE;
        }
        if (sign == 0) {
            return VelocityBand.FLAT;
        }
        if (velocityPpPerQuarter.compareTo(STRONG_THRESHOLD) >= 0) {
            return VelocityBand.STRONG;
        }
        if (velocityPpPerQuarter.compareTo(MODERATE_THRESHOLD) >= 0) {
            return VelocityBand.MODERATE;
        }
        return VelocityBand.WEAK;
    }
}
