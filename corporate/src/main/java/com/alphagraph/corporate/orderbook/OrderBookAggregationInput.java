package com.alphagraph.corporate.orderbook;

import com.alphagraph.corporate.api.OrderBookEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One instrument's full order-lifecycle ledger plus its prior snapshot value (if one exists), the
 * input to {@link OrderBookAggregationEngine}. {@code previousOrderBookCrore} is null on an
 * instrument's first-ever run - there is nothing to compare growth against yet.
 */
record OrderBookAggregationInput(
    UUID instrumentId, String symbol, List<OrderBookEntry> entries, Double previousOrderBookCrore, LocalDate asOfDate
) {
}
