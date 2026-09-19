package com.alphagraph.sector.transformation;

import com.alphagraph.common.inflection.VelocityBand;

import java.math.BigDecimal;

/**
 * Hardcoded Java threshold constants, never a DB rule - same convention
 * {@code ownership.transformation.VelocityBanding}/{@code financial.transformation.FinancialVelocityBanding}
 * already established, same thresholds too. Bands {@code change} directly - Sector's evidence has
 * no separate {@code velocity_*} column, but in this codebase "velocity" has never been a
 * separately-derived quantity from "change" (confirmed against
 * {@code market.transformation.MarketAccumulationEngine}, which constructs both fields from the
 * exact same value), so banding {@code change} here is equivalent, not an approximation.
 */
final class SectorVelocityBanding {

    private static final BigDecimal STRONG_THRESHOLD = new BigDecimal("2.0");
    private static final BigDecimal MODERATE_THRESHOLD = new BigDecimal("0.5");

    private SectorVelocityBanding() {
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
