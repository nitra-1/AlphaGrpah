package com.alphagraph.api.portfolio;

import java.math.BigDecimal;

/** One sector's share of total priced portfolio market value. */
public record SectorExposureDto(String sectorName, BigDecimal marketValue, BigDecimal percentOfPortfolio) {
}
