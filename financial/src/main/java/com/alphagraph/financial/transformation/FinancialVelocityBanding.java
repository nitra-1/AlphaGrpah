package com.alphagraph.financial.transformation;

import com.alphagraph.common.inflection.VelocityBand;

import java.math.BigDecimal;

/**
 * Hardcoded Java threshold constants, never a DB rule - same convention
 * {@code ownership.transformation.VelocityBanding}/{@code market.transformation.MarketVelocityBanding}
 * already established. Two band types: {@link #bandPercentagePoint} for every growth-rate/
 * acceleration quantity (never a raw currency delta - see {@code FinancialInflectionEngine}'s own
 * javadoc for why), and {@link #bandDecliningCurrencyPct} for {@code INTEREST_COST_DECLINING},
 * which bands a currency metric's own percentage change (never the raw rupee delta - ₹10cr of
 * decline means something different for a ₹100cr company than a ₹10,000cr one) and treats negative
 * as the "good"/strong direction.
 */
final class FinancialVelocityBanding {

    private static final BigDecimal STRONG_THRESHOLD = new BigDecimal("2.0");
    private static final BigDecimal MODERATE_THRESHOLD = new BigDecimal("0.5");

    private static final BigDecimal DECLINING_STRONG_THRESHOLD = new BigDecimal("-15");
    private static final BigDecimal DECLINING_MODERATE_THRESHOLD = new BigDecimal("-5");

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

    /** {@code changePct} is a percentage change (e.g. interest expense QoQ/YoY % change) - negative is the declining, "good" direction here, so the bands invert relative to {@link #bandPercentagePoint}. */
    static VelocityBand bandDecliningCurrencyPct(BigDecimal changePct) {
        int sign = changePct.signum();
        if (sign > 0) {
            return VelocityBand.NEGATIVE;
        }
        if (sign == 0) {
            return VelocityBand.FLAT;
        }
        if (changePct.compareTo(DECLINING_STRONG_THRESHOLD) <= 0) {
            return VelocityBand.STRONG;
        }
        if (changePct.compareTo(DECLINING_MODERATE_THRESHOLD) <= 0) {
            return VelocityBand.MODERATE;
        }
        return VelocityBand.WEAK;
    }
}
