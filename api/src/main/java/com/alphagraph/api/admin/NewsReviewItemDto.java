package com.alphagraph.api.admin;

import java.time.Instant;
import java.util.UUID;

public record NewsReviewItemDto(UUID id, String title, String source, String sourceUrl, Instant announcedAt, String extractedText) {
}
