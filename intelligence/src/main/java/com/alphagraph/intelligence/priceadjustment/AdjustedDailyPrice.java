package com.alphagraph.intelligence.priceadjustment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One trading day's raw close alongside its back-adjusted equivalent, plus the exact BONUS/SPLIT
 * actions that caused the adjustment - {@code rawClose} is never overwritten or hidden,
 * {@code appliedActions} is what lets a consumer show, not silently apply, the adjustment.
 */
public record AdjustedDailyPrice(
    UUID instrumentId, String symbol, LocalDate tradeDate,
    BigDecimal rawClose, BigDecimal adjustedClose, BigDecimal cumulativeFactor,
    List<AppliedAdjustment> appliedActions
) {
    public boolean isAdjusted() {
        return !appliedActions.isEmpty();
    }
}
