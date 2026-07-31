package com.alphagraph.corporate.events;

import java.util.List;

/**
 * Mirrors the JSON shape {@link CorporateEventEngine} constrains Claude's response to via
 * structured outputs ({@code output_config.format}) - field names match exactly, no
 * {@code @JsonProperty} needed. An empty {@code events} list is a valid, expected response: most
 * documents (routine filings, board-meeting intimations) describe none of the 13 named event
 * types.
 */
record LlmEventResponse(List<LlmEvent> events) {
}

/**
 * One event as Claude returns it. {@code eventType}/{@code revenueImpact}/{@code signal} are
 * plain strings here (not the {@code corporate.api} enums) since Jackson deserializes JSON schema
 * string+enum constraints as strings regardless - {@link CorporateEventEngine} does the
 * string-to-enum conversion explicitly, so a value the model produces outside the schema's
 * declared enum (should not happen given {@code additionalProperties: false}, but is not
 * impossible) fails loudly with a clear error rather than a silent Jackson enum-deserialization
 * exception deep in a stack trace.
 */
record LlmEvent(
    String eventType, String category, String summary,
    int confidence, String expectedDuration, String revenueImpact, String signal
) {
}
