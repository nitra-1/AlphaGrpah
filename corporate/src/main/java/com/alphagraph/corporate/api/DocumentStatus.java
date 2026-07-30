package com.alphagraph.corporate.api;

/**
 * A document's position in the Document Pipeline (Download -> OCR/Parse -> Extract -> Chunk ->
 * Entity Extraction -> Embeddings). PENDING means only metadata has been collected; DOWNLOADED
 * means the raw file bytes are stored; PROCESSED means chunks/entities/embeddings exist.
 * NEEDS_OCR is a real, currently-reachable terminal state - the sidecar has no OCR fallback yet
 * (Tesseract isn't installed on the dev machine), so a scanned/image-only PDF that yields almost
 * no extractable text lands here instead of silently producing empty chunks. FAILED is reserved
 * for a genuine download/processing error - defined for schema completeness but not yet reachable
 * from any code path in this module (same pattern as corporate.corporate_actions' unused
 * retry_count column from Module 1.4).
 */
public enum DocumentStatus {
    PENDING,
    DOWNLOADED,
    PROCESSED,
    NEEDS_OCR,
    FAILED
}
