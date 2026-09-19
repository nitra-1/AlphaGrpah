package com.alphagraph.market.transformation;

import com.alphagraph.common.inflection.VelocityBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MarketVelocityBandingTest {

    @Test
    void percentagePointBandsMatchTheirExactBoundaries() {
        assertThat(MarketVelocityBanding.bandPercentagePoint(new BigDecimal("2.00"))).isEqualTo(VelocityBand.STRONG);
        assertThat(MarketVelocityBanding.bandPercentagePoint(new BigDecimal("1.99"))).isEqualTo(VelocityBand.MODERATE);
        assertThat(MarketVelocityBanding.bandPercentagePoint(new BigDecimal("0.50"))).isEqualTo(VelocityBand.MODERATE);
        assertThat(MarketVelocityBanding.bandPercentagePoint(new BigDecimal("0.49"))).isEqualTo(VelocityBand.WEAK);
        assertThat(MarketVelocityBanding.bandPercentagePoint(BigDecimal.ZERO)).isEqualTo(VelocityBand.FLAT);
        assertThat(MarketVelocityBanding.bandPercentagePoint(new BigDecimal("-0.01"))).isEqualTo(VelocityBand.NEGATIVE);
    }

    @Test
    void ratioBandsMatchTheirExactBoundaries() {
        assertThat(MarketVelocityBanding.bandRatio(new BigDecimal("0.50"))).isEqualTo(VelocityBand.STRONG);
        assertThat(MarketVelocityBanding.bandRatio(new BigDecimal("0.49"))).isEqualTo(VelocityBand.MODERATE);
        assertThat(MarketVelocityBanding.bandRatio(new BigDecimal("0.20"))).isEqualTo(VelocityBand.MODERATE);
        assertThat(MarketVelocityBanding.bandRatio(new BigDecimal("0.19"))).isEqualTo(VelocityBand.WEAK);
        assertThat(MarketVelocityBanding.bandRatio(BigDecimal.ZERO)).isEqualTo(VelocityBand.FLAT);
        assertThat(MarketVelocityBanding.bandRatio(new BigDecimal("-0.10"))).isEqualTo(VelocityBand.NEGATIVE);
    }

    @Test
    void bandForRoutesRelativeVolumeToRatioAndEverythingElseToPercentagePoint() {
        assertThat(MarketVelocityBanding.bandFor(MarketMetric.RELATIVE_VOLUME, new BigDecimal("0.30"))).isEqualTo(VelocityBand.MODERATE);
        assertThat(MarketVelocityBanding.bandFor(MarketMetric.DELIVERY_PERCENTAGE_20D_AVG, new BigDecimal("0.30"))).isEqualTo(VelocityBand.WEAK);
        assertThat(MarketVelocityBanding.bandFor(MarketMetric.PRICE_RETURN_20D, new BigDecimal("0.30"))).isEqualTo(VelocityBand.WEAK);
    }
}
