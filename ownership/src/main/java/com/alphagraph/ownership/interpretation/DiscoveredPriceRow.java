package com.alphagraph.ownership.interpretation;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One {@code market.discovered_prices} row, as read by {@link DiscoveredPriceHistoryReader}. */
record DiscoveredPriceRow(
    LocalDate tradeDate, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
    long volume, BigDecimal dailyTradedValue, BigDecimal deliveryPercentage
) {
}
