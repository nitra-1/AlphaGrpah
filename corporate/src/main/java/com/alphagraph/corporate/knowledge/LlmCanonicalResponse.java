package com.alphagraph.corporate.knowledge;

import java.util.List;

/**
 * Mirrors the JSON shape {@link DocumentIntelligenceEngine} constrains Claude's response to via
 * structured outputs. {@code facts} is a list of key-value-unit triples rather than a nested
 * object with dynamic keys - JSON Schema's {@code additionalProperties: false} requires an
 * enumerable, fixed property set, which a genuinely open-ended fact bag can't satisfy; a list of
 * typed triples sidesteps that limitation and maps directly onto the
 * {@code corporate.document_facts} EAV table.
 */
record LlmCanonicalResponse(
    String documentType, String sentiment, int confidence, String summary,
    List<String> topics, List<LlmFact> facts
) {
}

/** One extracted fact. {@code unit} is an empty string, not null, when no unit applies - kept a plain required string field to avoid JSON Schema's limited nullable-type support. */
record LlmFact(String key, String value, String unit) {
}
