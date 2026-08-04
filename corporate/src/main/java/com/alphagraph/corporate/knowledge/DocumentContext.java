package com.alphagraph.corporate.knowledge;

import java.util.UUID;

/** Everything a {@link DocumentExtractor} needs to run Stage 2 extraction on one document. */
public record DocumentContext(
    UUID documentId, UUID instrumentId, String symbol, String documentText, DocumentClassification classification
) {
}
