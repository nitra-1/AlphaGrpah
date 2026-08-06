package com.alphagraph.decision.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One executed trade (Module 3.8) - immutable, auto-recorded as a byproduct of
 * {@code decision.portfolio.PortfolioService.buy}/{@code sell}, never a separate manual-entry
 * surface (avoids recording the same trade twice). {@code costBasisPrice}/{@code realizedPnl}
 * are null for a BUY - nothing is realized until a position is (partially or fully) sold.
 */
public record TradeJournalEntry(
    UUID id, UUID instrumentId, String symbol, TradeAction action,
    BigDecimal quantity, BigDecimal price, BigDecimal costBasisPrice, BigDecimal realizedPnl,
    String rationale, Instant createdAt
) {
}
