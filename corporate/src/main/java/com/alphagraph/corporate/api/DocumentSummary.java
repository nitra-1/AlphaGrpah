package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.UUID;

/**
 * The Document Intelligence Engine's document-level synthesis, mirroring
 * {@code corporate.document_summary} (docs/003_Database_Architecture.md §3a) - 1:1 with a
 * document, unlike {@link DocumentFact}/topics which are 1:many. {@code documentType} is
 * deliberately a free string, not a CHECK-constrained enum (e.g. "ORDER_ANNOUNCEMENT",
 * "CAPACITY_EXPANSION", "FINANCIAL_RESULT") - same reasoning as {@code CorporateEvent.category}:
 * constraining it to a fixed taxonomy now would mean guessing categories nobody has specified for
 * every future document-consuming engine.
 */
public record DocumentSummary(
    UUID documentId, String documentType, Sentiment sentiment, double confidence, String summary, Instant extractedAt
) {
}
