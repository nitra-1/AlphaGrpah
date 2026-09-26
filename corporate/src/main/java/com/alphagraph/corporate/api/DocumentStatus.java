package com.alphagraph.corporate.api;

/**
 * A document's position in the shared Document Pipeline (Download -> OCR/Parse -> Extract ->
 * Chunk -> Entity Extraction -> Embeddings -> Canonical Knowledge Extraction). PENDING means only
 * metadata has been collected; DOWNLOADED means the raw file bytes are stored; PROCESSED means
 * chunks/entities/embeddings exist. NEEDS_OCR is a real, currently-reachable terminal state - the
 * sidecar has no OCR fallback yet (Tesseract isn't installed on the dev machine), so a
 * scanned/image-only PDF that yields almost no extractable text lands here instead of silently
 * producing empty chunks. FAILED is reserved for a genuine download/processing error - defined
 * for schema completeness but not yet reachable from any code path in this module (same pattern
 * as corporate.corporate_actions' unused retry_count column from Module 1.4).
 *
 * <p>KNOWLEDGE_EXTRACTED (Module 2.2, revived) means the Document Intelligence Engine has run its
 * single canonical Claude call over this document's text, populating
 * document_facts/document_topics/document_summary - reached whether or not any facts/topics were
 * actually found. This status intentionally does NOT track any individual downstream consumer
 * (Corporate Event Engine, Order Book Engine, ...): once a document reaches KNOWLEDGE_EXTRACTED,
 * every independent rule engine reads the same canonical output and tracks its own idempotency via
 * {@code corporate.document_consumer_checkpoints}, not via this status. This replaces Module 2.3's
 * original EVENTS_EXTRACTED value, which implied a single specific consumer and stopped fitting
 * once a second engine (Order Book, Module 2.4) needed to read the same documents independently.
 *
 * <p>PENDING_REVIEW and DISCARDED (Module 2.6 pre-filter retrofit) are legacy values, kept only
 * for existing rows' own history/audit trail - **nothing writes them anymore** since the News &
 * Economic Discovery rework retired the human relevance-triage queue entirely. NOT_ECONOMIC is
 * their replacement: {@code corporate.newsfeed.NonEconomicPreFilter} runs a cheap, inclusion-
 * biased keyword check before a collected news article reaches PROCESSED - a clear non-match
 * (sports/entertainment/celebrity/crime vocabulary) lands in NOT_ECONOMIC, a terminal state that
 * needs no human action, same as DISCARDED did before. The real relevance classification is
 * {@code corporate.knowledge.NewsExtractor}'s own {@code economicRelevance} field, run
 * automatically on every article that clears the cheap pre-filter - never gated behind a human.
 */
public enum DocumentStatus {
    PENDING,
    DOWNLOADED,
    PROCESSED,
    NEEDS_OCR,
    FAILED,
    KNOWLEDGE_EXTRACTED,
    PENDING_REVIEW,
    DISCARDED,
    NOT_ECONOMIC
}
