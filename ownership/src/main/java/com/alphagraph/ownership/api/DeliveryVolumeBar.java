package com.alphagraph.ownership.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The Institutional Engine's minimal view of one day's market activity - just delivery % and
 * volume, not the full OHLC bar {@code technical.api.DailyBar} needed. Deliberately not
 * {@code market.api.DailyPrice}: domain modules never depend on each other directly
 * (docs/001_System_Architecture.md §4), so {@code intelligence} maps market's published type into
 * this one before calling {@link com.alphagraph.ownership.engine.InstitutionalEngine}.
 */
public record DeliveryVolumeBar(LocalDate tradeDate, BigDecimal deliveryPercentage, long volume) {
}
