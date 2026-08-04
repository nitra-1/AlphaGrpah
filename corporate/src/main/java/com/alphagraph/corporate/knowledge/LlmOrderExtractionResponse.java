package com.alphagraph.corporate.knowledge;

/**
 * Mirrors the fixed-shape JSON {@link OrderExtractor} constrains Claude's response to. Unlike
 * Stage 1's response, this is a fixed set of known fields (not a generic key-value list) - a
 * narrow Stage 2 extractor knows its exact field set in advance, so there's no need for the
 * flexibility Stage 1 requires. Every field is an empty string when not found in the text - this
 * extractor may run on a document that turns out not to actually be order-related despite Stage
 * 1's routing hint, in which case {@code orderLifecycleStage} comes back empty and
 * {@link OrderExtractor} emits no facts at all.
 */
record LlmOrderExtractionResponse(
    String customer, String orderValue, String currency, String businessUnit,
    String executionStartYear, String executionMonths,
    String orderScope, String orderSector, String orderRecurrence, String orderLifecycleStage,
    int confidence
) {
}
