package com.alphagraph.sector.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The Sector Engine's minimal view of one day's activity for one constituent - just close and
 * volume, not the full OHLC {@code technical.api.DailyBar} needed. Deliberately not
 * {@code market.api.DailyPrice}: domain modules never depend on each other directly
 * (docs/001_System_Architecture.md §4), so {@code intelligence} maps market's published type into
 * this one before calling {@link com.alphagraph.sector.engine.SectorEngine}.
 */
public record SectorBar(LocalDate tradeDate, BigDecimal close, long volume) {
}
