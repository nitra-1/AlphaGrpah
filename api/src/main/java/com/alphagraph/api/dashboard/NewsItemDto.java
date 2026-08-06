package com.alphagraph.api.dashboard;

import java.time.Instant;

/** "Positive News" / "Negative News" widget row. */
public record NewsItemDto(String symbol, String direction, String signal, String impactSummary, Instant announcedAt) {
}
