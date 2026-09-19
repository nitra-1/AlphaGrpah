package com.alphagraph.sector.transformation;

import com.alphagraph.common.inflection.VelocityBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SectorVelocityBandingTest {

    @Test
    void bandsMatchTheirExactBoundaries() {
        assertThat(SectorVelocityBanding.bandPercentagePoint(new BigDecimal("2.00"))).isEqualTo(VelocityBand.STRONG);
        assertThat(SectorVelocityBanding.bandPercentagePoint(new BigDecimal("1.99"))).isEqualTo(VelocityBand.MODERATE);
        assertThat(SectorVelocityBanding.bandPercentagePoint(new BigDecimal("0.50"))).isEqualTo(VelocityBand.MODERATE);
        assertThat(SectorVelocityBanding.bandPercentagePoint(new BigDecimal("0.49"))).isEqualTo(VelocityBand.WEAK);
        assertThat(SectorVelocityBanding.bandPercentagePoint(BigDecimal.ZERO)).isEqualTo(VelocityBand.FLAT);
        assertThat(SectorVelocityBanding.bandPercentagePoint(new BigDecimal("-0.01"))).isEqualTo(VelocityBand.NEGATIVE);
    }
}
