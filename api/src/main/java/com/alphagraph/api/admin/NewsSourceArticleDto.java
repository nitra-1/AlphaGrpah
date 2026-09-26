package com.alphagraph.api.admin;

import java.time.Instant;
import java.util.UUID;

public record NewsSourceArticleDto(UUID documentId, String title, String sourceUrl, Instant announcedAt) {
}
