package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.UUID;

/** A collected news article {@link com.alphagraph.corporate.newsfeed.NewsRelevanceFilter} couldn't match to any tracked instrument, awaiting an admin's keep/discard decision (Module 2.6 pre-filter retrofit). */
public record NewsReviewItem(UUID id, String title, String source, String sourceUrl, Instant announcedAt, String extractedText) {
}
