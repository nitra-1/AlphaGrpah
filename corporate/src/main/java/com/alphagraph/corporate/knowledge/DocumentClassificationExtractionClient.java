package com.alphagraph.corporate.knowledge;

/**
 * One provider's ability to run {@link DocumentIntelligenceEngine}'s Stage 1 classification call -
 * identical prompt, identical JSON schema (see {@link DocumentIntelligenceEngine#buildPrompt}/
 * {@link DocumentIntelligenceEngine#buildSchemaMap}) regardless of implementation, so
 * {@link DocumentIntelligenceEngine#parseClassification} never needs to know which provider
 * actually produced the raw JSON.
 */
interface DocumentClassificationExtractionClient {

    /** Returns the raw JSON text a structured-output call produced for this document's text. Throws on any failure. */
    String extractRawJson(String documentText);
}
