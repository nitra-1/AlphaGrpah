package com.alphagraph.technical.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day's OHLCV bar as the Technical Engine consumes it — deliberately not
 * {@code market.api.DailyPrice}: domain modules never depend on each other directly
 * (docs/001_System_Architecture.md §4), so {@code intelligence} maps market's published type into
 * this one before calling {@link com.alphagraph.technical.engine.TechnicalEngine}.
 */
public record DailyBar(
    LocalDate tradeDate, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
    long volume, BigDecimal deliveryPercentage
) {
}
