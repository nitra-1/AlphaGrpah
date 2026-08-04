package com.alphagraph.corporate.knowledge;

import java.util.List;

/**
 * Mirrors the JSON shape Stage 1 ({@link DocumentIntelligenceEngine}) constrains Claude's
 * response to. Deliberately has no {@code facts} field - structured business-fact extraction is
 * entirely Stage 2's job now (see {@link DocumentExtractor}), so this stays intentionally lean.
 */
record LlmClassificationResponse(
    String documentType, List<String> topics, List<String> entities, String summary,
    String sentiment, int confidence, List<String> recommendedExtractors
) {
}
