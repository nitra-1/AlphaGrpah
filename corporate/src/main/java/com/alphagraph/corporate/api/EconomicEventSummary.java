package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the "Latest Economic Events" feed - {@code corporate.economic_events} plus a live
 * count of how many other events share this one's {@code cluster_key} (the v1 dedup proxy: same
 * theme, same calendar date), so "N articles" is answered at read time rather than stored.
 */
public record EconomicEventSummary(
    UUID id, String theme, String economicRelevance, String direction, String magnitude,
    double confidence, String horizon, int sourceArticleCount, Instant publishedAt, Instant computedAt
) {
}
