package com.alphagraph.corporate.knowledge;

import java.util.List;

/**
 * Mirrors the JSON shape {@link ManagementExtractor} constrains Claude's response to. Unlike
 * {@link LlmOrderExtractionResponse} (at most one order per document), a single earnings call or
 * investor presentation can genuinely contain several distinct forward-looking statements at
 * once (revenue guidance AND margin guidance AND capex commentary), so this is a list.
 */
record LlmManagementExtractionResponse(List<LlmManagementStatement> statements) {
}

/** One forward-looking statement. {@code valueNumeric} is an empty string when the statement is qualitative prose with nothing to parse (most Demand/Pricing/Competition/Risk commentary). */
record LlmManagementStatement(
    String metricType, String valueText, String valueNumeric, String period,
    String direction, String signal, String commitmentLevel, int confidence,
    String relatedEntityName, String relatedEntityType, String relationshipType
) {
}
