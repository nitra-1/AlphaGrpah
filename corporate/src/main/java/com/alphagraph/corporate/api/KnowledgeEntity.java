package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One resolved, canonical entity - mirrors {@code knowledge.entity_master}. {@code aliases}
 * exists because normalization alone (case/whitespace/corporate-suffix stripping) can't unify
 * true synonyms like "BEL" and "Bharat Electronics Limited" - those are genuinely different
 * words, not just formatting variants - so known alternate names are recorded explicitly instead.
 */
public record KnowledgeEntity(
    UUID id, EntityType entityType, String canonicalName, List<String> aliases, String status,
    UUID linkedInstrumentId, UUID linkedSectorId, Instant createdAt, Instant updatedAt
) {
}
