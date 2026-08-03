package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One order-lifecycle event, mirroring {@code corporate.order_book_ledger}
 * (docs/003_Database_Architecture.md §3a) - parsed from a document's canonical facts (Module 2.2)
 * by the Order Book Engine (Module 2.4). Most fields are nullable: not every order-related
 * document mentions a customer name, a business unit, or precise execution dates, and a null here
 * means the fact genuinely wasn't extracted, not a parsing failure.
 */
public record OrderBookEntry(
    UUID id, UUID documentId, UUID instrumentId, String symbol,
    String customer, Double orderValueCrore, String businessUnit,
    String executionStart, String executionEnd,
    OrderScope orderScope, OrderSector orderSector, OrderRecurrence orderRecurrence,
    OrderLifecycleStage lifecycleStage, Instant detectedAt
) {
}
