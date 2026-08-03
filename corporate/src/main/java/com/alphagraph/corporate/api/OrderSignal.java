package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.UUID;

/** One detected Order Book signal, mirroring {@code corporate.order_book_signals}. */
public record OrderSignal(
    UUID instrumentId, String symbol, OrderSignalType signalType,
    UUID relatedOrderId, String detail, Instant detectedAt
) {
}
