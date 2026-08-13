package com.alphagraph.intelligence.priceadjustment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One trading day's raw close alongside its back-adjusted equivalent, plus the exact BONUS/SPLIT
 * actions that caused the adjustment - {@code rawClose} is never overwritten or hidden,
 * {@code appliedActions} is what lets a consumer show, not silently apply, the adjustment.
 *
 * {@code rawHigh}/{@code adjustedHigh} and {@code rawLow}/{@code adjustedLow} use the exact same
 * {@code cumulativeFactor} as close - a BONUS/SPLIT scales the entire OHLC uniformly, so this is
 * the same adjustment already applied to close, just carried through to the other two fields.
 * Added for MFE/MAE (max favorable/adverse excursion), which need the adjusted intraday range,
 * not just the adjusted close.
 */
public record AdjustedDailyPrice(
    UUID instrumentId, String symbol, LocalDate tradeDate,
    BigDecimal rawClose, BigDecimal adjustedClose,
    BigDecimal rawHigh, BigDecimal adjustedHigh, BigDecimal rawLow, BigDecimal adjustedLow,
    BigDecimal cumulativeFactor, List<AppliedAdjustment> appliedActions
) {
    public boolean isAdjusted() {
        return !appliedActions.isEmpty();
    }
}
