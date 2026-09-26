package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.UUID;

/** One source article behind an {@link EconomicEventDetail} - every document sharing that event's {@code cluster_key}. */
public record NewsSourceArticle(UUID documentId, String title, String sourceUrl, Instant announcedAt) {
}
