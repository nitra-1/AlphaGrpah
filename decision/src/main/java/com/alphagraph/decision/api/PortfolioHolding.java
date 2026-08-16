package com.alphagraph.decision.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One current position in the single global portfolio (Module 3.3) - quantity and avgBuyPrice
 * are the weighted-average cost basis after every buy, not a transaction record. Individual
 * buy/sell events are Module 3.8's (Trade Journal) job, not this module's. Live price/P&L and
 * Rank/Score/Risk enrichment are api-layer concerns, the same split
 * {@code decision.api.WatchlistItem} already established.
 *
 * {@code createdAt} means "the first time AlphaGraph recorded this holding" - it is set once by
 * {@code PortfolioStore.upsert()}'s {@code INSERT} and never touched by a later averaging-in
 * buy's {@code ON CONFLICT DO UPDATE}, so it survives as a stable anchor even after repeat buys.
 * It is NOT a proven economic purchase date - for an imported portfolio, a holding entered after
 * the actual trade, or a re-entry after a full exit, this is when the row was created, not
 * necessarily when the investor bought the stock. Position Health (api.portfolio) uses it as a
 * "first entry" anchor on exactly this understanding; a future Trade Journal-backed
 * {@code position_cycle_id}/{@code entry_trade_date} would be the more precise source once it
 * exists.
 */
public record PortfolioHolding(UUID id, UUID instrumentId, String symbol, BigDecimal quantity, BigDecimal avgBuyPrice, Instant updatedAt, Instant createdAt) {
}
