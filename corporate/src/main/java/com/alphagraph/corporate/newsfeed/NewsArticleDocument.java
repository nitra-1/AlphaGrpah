package com.alphagraph.corporate.newsfeed;

import com.alphagraph.corporate.api.DocumentSource;

import java.time.Instant;

/**
 * A normalized news article, ready to load - deliberately NOT {@code corporate.api.
 * CorporateDocument} (which has no {@code instrumentId}/{@code extractedText} carried this way).
 * Unlike the PDF-download path (Module 2.1), there's no separate "download" stage - the article
 * text arrives directly from the RSS feed, so the normalized record carries it straight through
 * to {@link NewsFeedLoader}, which writes it with status PROCESSED immediately.
 */
record NewsArticleDocument(
    DocumentSource source, String externalId, String category, String title,
    String sourceUrl, Instant announcedAt, String extractedText
) {
}
