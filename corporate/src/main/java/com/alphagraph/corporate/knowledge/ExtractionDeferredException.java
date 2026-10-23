package com.alphagraph.corporate.knowledge;

/**
 * "Not now - try this document again on the next sweep." Thrown instead of spending Claude when
 * Gemini can't answer a news document (see {@link GeminiUnavailableException}) or today's Claude
 * fallback allowance is used up ({@link ClaudeFallbackBudget}). {@link DocumentRouter} lets this
 * one through while still swallowing every other extractor failure, and {@link
 * KnowledgeExtractionOrchestrator} counts it separately from real failures - the document stays
 * {@code PROCESSED}, so the next sweep picks it up again.
 */
class ExtractionDeferredException extends IllegalStateException {

    ExtractionDeferredException(String message, Throwable cause) {
        super(message, cause);
    }
}
