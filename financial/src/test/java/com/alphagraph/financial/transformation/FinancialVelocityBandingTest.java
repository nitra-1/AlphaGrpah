package com.alphagraph.financial.transformation;

import com.alphagraph.common.inflection.VelocityBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialVelocityBandingTest {

    @Test
    void bandsMatchTheirExactBoundaries() {
        assertThat(FinancialVelocityBanding.bandPercentagePoint(new BigDecimal("2.00"))).isEqualTo(VelocityBand.STRONG);
        assertThat(FinancialVelocityBanding.bandPercentagePoint(new BigDecimal("1.99"))).isEqualTo(VelocityBand.MODERATE);
        assertThat(FinancialVelocityBanding.bandPercentagePoint(new BigDecimal("0.50"))).isEqualTo(VelocityBand.MODERATE);
        assertThat(FinancialVelocityBanding.bandPercentagePoint(new BigDecimal("0.49"))).isEqualTo(VelocityBand.WEAK);
        assertThat(FinancialVelocityBanding.bandPercentagePoint(BigDecimal.ZERO)).isEqualTo(VelocityBand.FLAT);
        assertThat(FinancialVelocityBanding.bandPercentagePoint(new BigDecimal("-0.01"))).isEqualTo(VelocityBand.NEGATIVE);
    }

    @Test
    void decliningCurrencyPctBandsMatchTheirExactBoundaries_negativeIsTheStrongDirection() {
        assertThat(FinancialVelocityBanding.bandDecliningCurrencyPct(new BigDecimal("-15.00"))).isEqualTo(VelocityBand.STRONG);
        assertThat(FinancialVelocityBanding.bandDecliningCurrencyPct(new BigDecimal("-14.99"))).isEqualTo(VelocityBand.MODERATE);
        assertThat(FinancialVelocityBanding.bandDecliningCurrencyPct(new BigDecimal("-5.00"))).isEqualTo(VelocityBand.MODERATE);
        assertThat(FinancialVelocityBanding.bandDecliningCurrencyPct(new BigDecimal("-4.99"))).isEqualTo(VelocityBand.WEAK);
        assertThat(FinancialVelocityBanding.bandDecliningCurrencyPct(BigDecimal.ZERO)).isEqualTo(VelocityBand.FLAT);
        assertThat(FinancialVelocityBanding.bandDecliningCurrencyPct(new BigDecimal("0.01"))).isEqualTo(VelocityBand.NEGATIVE);
    }
}
