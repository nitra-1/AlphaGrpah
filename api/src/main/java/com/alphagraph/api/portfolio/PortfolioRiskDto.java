package com.alphagraph.api.portfolio;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregate risk across the whole portfolio (Module 3.4) - distinct from each holding's own
 * riskLevel already shown by {@link PortfolioEntryDto} ("is TCS itself risky?" vs. "is this
 * portfolio, as a whole, over-concentrated or under-diversified?"). Every field is null/empty on
 * an empty portfolio, or when no holding has a priced market value yet - there's nothing to
 * weight or concentrate against. weightedRiskScore/weightedRiskLevel are computed only over
 * holdings that have BOTH a live price AND a Risk Score - riskScoreCoveragePercent discloses what
 * fraction of the portfolio's priced value that actually reflects, the same "don't silently treat
 * missing data as zero" principle every score-carrying DTO in this project follows.
 */
public record PortfolioRiskDto(
    BigDecimal totalMarketValue,
    Double weightedRiskScore, String weightedRiskLevel, Double riskScoreCoveragePercent,
    String topHoldingSymbol, BigDecimal topHoldingConcentrationPercent, String holdingConcentrationLevel,
    String topSectorName, BigDecimal topSectorConcentrationPercent, String sectorConcentrationLevel,
    List<SectorExposureDto> sectorBreakdown
) {
}
