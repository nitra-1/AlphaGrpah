package com.alphagraph.ownership.transformation;

import com.alphagraph.common.inflection.VelocityBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VelocityBandingTest {

    @Test
    void bandsMatchTheirExactBoundaries() {
        assertThat(VelocityBanding.band(new BigDecimal("2.00"))).isEqualTo(VelocityBand.STRONG);
        assertThat(VelocityBanding.band(new BigDecimal("2.01"))).isEqualTo(VelocityBand.STRONG);
        assertThat(VelocityBanding.band(new BigDecimal("1.99"))).isEqualTo(VelocityBand.MODERATE);
        assertThat(VelocityBanding.band(new BigDecimal("0.50"))).isEqualTo(VelocityBand.MODERATE);
        assertThat(VelocityBanding.band(new BigDecimal("0.49"))).isEqualTo(VelocityBand.WEAK);
        assertThat(VelocityBanding.band(new BigDecimal("0.01"))).isEqualTo(VelocityBand.WEAK);
        assertThat(VelocityBanding.band(BigDecimal.ZERO)).isEqualTo(VelocityBand.FLAT);
        assertThat(VelocityBanding.band(new BigDecimal("-0.01"))).isEqualTo(VelocityBand.NEGATIVE);
        assertThat(VelocityBanding.band(new BigDecimal("-5.00"))).isEqualTo(VelocityBand.NEGATIVE);
    }
}
