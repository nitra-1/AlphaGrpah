package com.alphagraph.api.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The "announce, don't silently adjust" surface for a BONUS/SPLIT corporate action - shown on the
 * dashboard so a user sees exactly which instruments' historical charts/scores are now
 * back-adjusted and by how much, rather than discovering an adjusted price with no explanation.
 */
public record PriceAdjustmentDto(
    String symbol, String actionType, LocalDate exDate,
    Integer ratioNumerator, Integer ratioDenominator, BigDecimal adjustmentFactor
) {
}
