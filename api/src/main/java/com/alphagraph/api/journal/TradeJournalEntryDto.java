package com.alphagraph.api.journal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeJournalEntryDto(
    UUID instrumentId, String symbol, String action,
    BigDecimal quantity, BigDecimal price, BigDecimal tradeValue,
    BigDecimal costBasisPrice, BigDecimal realizedPnl,
    String rationale, Instant createdAt
) {
}
