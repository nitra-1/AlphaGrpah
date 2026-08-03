package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.Sentiment;

import java.util.List;

/**
 * One document's canonical extraction result, before it has a document identity -
 * {@link KnowledgeExtractionOrchestrator} attaches that at persistence time.
 * {@code rawResponse} carries the engine's full JSON response verbatim, for auditability.
 */
record CanonicalExtraction(
    String documentType, Sentiment sentiment, double confidence, String summary,
    List<String> topics, List<ExtractedFact> facts, String rawResponse
) {
}

/** One extracted business fact, with its (already-normalized) key. */
record ExtractedFact(String factType, String value, String unit, double confidence) {
}
