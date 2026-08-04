package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Layer 1: one immutable (news document, affected company) link - one row per company a news
 * item materially affects, resolved against AlphaGraph's tracked instrument universe. A document
 * mentioning six companies produces up to six of these (fewer if some aren't tracked instruments -
 * see {@code corporate.news.NewsInstrumentMatcher}).
 */
public record NewsInstrumentLink(
    UUID id, UUID documentId, UUID instrumentId, String symbol, NewsImpactDirection direction,
    String signal, String impactSummary, double extractionConfidence, Instant announcedAt
) {
}
