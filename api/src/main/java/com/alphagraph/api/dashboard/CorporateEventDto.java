package com.alphagraph.api.dashboard;

import java.time.Instant;

/** "Corporate Events" widget row. */
public record CorporateEventDto(String symbol, String eventType, String category, String summary, String revenueImpact, String signal, Instant extractedAt) {
}
