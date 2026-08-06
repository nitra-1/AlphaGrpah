package com.alphagraph.corporate.api;

import java.util.UUID;

/**
 * One graph edge with both endpoints' display names already resolved - unlike
 * {@link KnowledgeRelationship} (which carries only entity IDs, the storage shape), this is the
 * read shape a consumer that wants to narrate a relationship (e.g. Module 2.9's AI Analyst) needs,
 * so it never has to resolve a name itself.
 */
public record RelationshipEdge(
    UUID fromEntityId, String fromEntityName, RelationshipType relationshipType,
    UUID toEntityId, String toEntityName, double confidence
) {
}
