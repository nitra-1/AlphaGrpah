package com.alphagraph.corporate.knowledge;

/**
 * Stage 2: specialized extraction. Every implementation is scoped to ONE narrow domain (orders,
 * management commentary, ...) with its own focused Claude prompt - "Order Prompt knows only about
 * Customer/Value/Duration/Execution, nothing else" - rather than one shared prompt that grows
 * with every new engine. {@link DocumentRouter} discovers every Spring-registered implementation
 * automatically and dispatches based on {@link #supports}, so adding a new extractor (e.g. a
 * future {@code PatentExtractor}) never requires changing the router or any existing extractor.
 */
public interface DocumentExtractor {

    /** Whether this extractor should run against a document with this Stage 1 classification. */
    boolean supports(DocumentClassification classification);

    /** Runs this extractor's own Claude call and returns the facts it found. Only called when {@link #supports} returned true. */
    ExtractionResult extract(DocumentContext context);
}
