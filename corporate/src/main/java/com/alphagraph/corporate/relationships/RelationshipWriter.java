package com.alphagraph.corporate.relationships;

import com.alphagraph.corporate.api.RelationshipType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Inserts one {@code knowledge.relationship} edge, idempotently. Document-sourced edges rely on
 * the table's {@code (from, type, to, source_document_id)} unique constraint; seeded edges (no
 * source document, e.g. {@link CompetitorGroupExpander}'s COMPETES_WITH pairs) rely on the
 * partial unique index over the same three columns where {@code source_document_id IS NULL}.
 * Either way, {@code ON CONFLICT DO NOTHING} makes re-processing the same document, or
 * re-expanding the same competitor group, a safe no-op rather than a duplicate row.
 */
@Component
class RelationshipWriter {

    private final JdbcTemplate jdbcTemplate;

    RelationshipWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(
        UUID fromEntityId, RelationshipType relationshipType, UUID toEntityId,
        UUID sourceDocumentId, double confidence, String createdByEngine
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO knowledge.relationship
                (from_entity_id, relationship_type, to_entity_id, source_document_id, confidence, created_by_engine)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
            fromEntityId, relationshipType.name(), toEntityId, sourceDocumentId, confidence, createdByEngine
        );
    }
}
