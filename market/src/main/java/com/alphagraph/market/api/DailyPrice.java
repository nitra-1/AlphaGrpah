package com.alphagraph.market.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Normalized daily OHLC + volume + delivery for one instrument, mirroring
 * {@code market.daily_prices} (docs/003_Database_Architecture.md §3a). Market cap is
 * deliberately absent — no real NSE daily report carries it directly (Module 1.1).
 */
public record DailyPrice(
    UUID instrumentId, String symbol, LocalDate tradeDate,
    BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
    long volume, BigDecimal deliveryPercentage
) {
}
