package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One chunk of a processed document's text plus its embedding, mirroring
 * {@code corporate.document_chunks} (docs/003_Database_Architecture.md §3a). {@code embedding}
 * is 384-dimensional, matching the NLP sidecar's model (sentence-transformers/all-MiniLM-L6-v2).
 */
public record DocumentChunk(
    UUID id, UUID documentId, int chunkIndex, String text, int wordCount,
    float[] embedding, List<DocumentEntity> entities, Instant createdAt
) {
}
