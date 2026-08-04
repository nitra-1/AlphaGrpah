package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.Sentiment;

import java.util.List;

/**
 * Stage 1's output ("Document Understanding") - lightweight classification, never structured
 * business facts. {@code documentType} is deliberately a free string, not a CHECK-constrained
 * enum (same reasoning as {@code CorporateEvent.category}): constraining it now would mean
 * guessing a taxonomy nobody has specified for every future document-consuming extractor.
 * {@code recommendedExtractors} is Stage 1's own routing signal - a {@link DocumentExtractor}'s
 * {@code supports()} typically checks whether its own name appears here, so adding a new
 * extractor never requires touching {@link DocumentRouter} or Stage 1's own code, only (at most)
 * a documentation nudge in Stage 1's prompt about the new category name.
 */
public record DocumentClassification(
    String documentType, List<String> topics, List<String> entities, String summary,
    Sentiment sentiment, double confidence, List<String> recommendedExtractors
) {
}
