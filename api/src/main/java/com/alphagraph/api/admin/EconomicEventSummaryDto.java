package com.alphagraph.api.admin;

import java.time.Instant;
import java.util.UUID;

public record EconomicEventSummaryDto(
    UUID id, String theme, String economicRelevance, String direction, String magnitude,
    double confidence, String horizon, int sourceArticleCount, Instant publishedAt, Instant computedAt
) {
}
