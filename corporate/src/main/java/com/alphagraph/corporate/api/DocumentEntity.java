package com.alphagraph.corporate.api;

/**
 * One named entity extracted from a document chunk, mirroring {@code corporate.document_entities}
 * (docs/003_Database_Architecture.md §3a). {@code entityType} is one of spaCy's default English
 * NER labels (ORG, PERSON, GPE, DATE, MONEY, ...) - the sidecar's model determines the label set,
 * not this system.
 */
public record DocumentEntity(String text, String entityType) {
}
