package com.alphagraph.corporate.events;

import com.alphagraph.corporate.api.Sentiment;

import java.util.List;
import java.util.Map;

/**
 * One document's canonical knowledge (Module 2.2's output: {@code corporate.document_topics} +
 * {@code document_facts} + {@code document_summary}), as read by {@link KnowledgeDocumentReader}
 * and classified by {@link CorporateEventEngine}. {@code facts} keys are already normalized
 * (lowercased, non-alphanumeric stripped) - the same normalization
 * {@code DocumentIntelligenceEngine} applied at write time.
 */
record KnowledgeContext(
    List<String> topics, Map<String, FactValue> facts, String documentType, String summary,
    Sentiment sentiment, double confidence
) {
}

/** One fact's value and unit, keyed by normalized fact type in {@link KnowledgeContext#facts()}. */
record FactValue(String value, String unit) {
}
