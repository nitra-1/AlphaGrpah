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
 */
public record PortfolioHolding(UUID id, UUID instrumentId, String symbol, BigDecimal quantity, BigDecimal avgBuyPrice, Instant updatedAt) {
}
