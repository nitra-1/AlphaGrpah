package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.UUID;

/** One directed, typed edge - mirrors {@code knowledge.relationship}. */
public record KnowledgeRelationship(
    UUID id, UUID fromEntityId, RelationshipType relationshipType, UUID toEntityId, UUID sourceDocumentId,
    Instant validFrom, Instant validTo, double confidence, String createdByEngine, Instant createdAt
) {
}
