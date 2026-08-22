package com.alphagraph.corporate.knowledge;

/**
 * One provider's ability to run {@link NewsExtractor}'s Stage 2 call - identical prompt, identical
 * JSON schema (see {@link NewsExtractor#buildPrompt}/{@link NewsExtractor#buildSchemaMap}) regardless
 * of implementation, so {@link NewsExtractor#parseResult} never needs to know which provider
 * actually produced the raw JSON.
 */
interface NewsImpactExtractionClient {

    /** Returns the raw JSON text a structured-output call produced for this document's text. Throws on any failure. */
    String extractRawJson(String documentText);
}
