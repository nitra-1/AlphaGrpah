package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.DocumentFact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

record PendingEconomicEventDocument(UUID id, Instant announcedAt, List<DocumentFact> facts) {
}

/**
 * Reads KNOWLEDGE_EXTRACTED documents not yet checkpointed by the Economic Event engine - its own
 * distinct consumer key ({@code ECONOMIC_EVENT_ENGINE}), independent of {@link
 * NewsLinkReader#CONSUMER} - both consumers read the same underlying {@code
 * corporate.document_facts}, each tracking its own idempotency via {@code
 * corporate.document_consumer_checkpoints}, same pattern every prior independent rule engine in
 * this codebase already uses. Mirrors {@link PendingNewsDocumentReader} exactly.
 */
@Component
class PendingEconomicEventDocumentReader {

    static final String CONSUMER = "ECONOMIC_EVENT_ENGINE";

    private final JdbcTemplate jdbcTemplate;

    PendingEconomicEventDocumentReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<PendingEconomicEventDocument> findUnprocessed() {
        List<UUID> documentIds = jdbcTemplate.query(
            """
            SELECT d.id FROM corporate.documents d
            WHERE d.status = 'KNOWLEDGE_EXTRACTED' AND d.source = 'NEWS'
              AND NOT EXISTS (
                  SELECT 1 FROM corporate.document_consumer_checkpoints c
                  WHERE c.document_id = d.id AND c.consumer = ?
              )
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            CONSUMER
        );

        List<PendingEconomicEventDocument> documents = new ArrayList<>();
        for (UUID documentId : documentIds) {
            documents.add(readOne(documentId));
        }
        return documents;
    }

    private PendingEconomicEventDocument readOne(UUID documentId) {
        Instant announcedAt = jdbcTemplate.queryForObject(
            "SELECT announced_at FROM corporate.documents WHERE id = ?",
            (rs, rowNum) -> rs.getTimestamp("announced_at").toInstant(),
            documentId
        );

        List<DocumentFact> facts = jdbcTemplate.query(
            """
            SELECT id, document_id, fact_type, fact_value, unit, confidence, created_at, commitment_level, fact_group
            FROM corporate.document_facts WHERE document_id = ? AND fact_group IS NOT NULL
            """,
            (rs, rowNum) -> new DocumentFact(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("document_id"), rs.getString("fact_type"),
                rs.getString("fact_value"), rs.getString("unit"), rs.getDouble("confidence"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("commitment_level"), (UUID) rs.getObject("fact_group")
            ),
            documentId
        );

        return new PendingEconomicEventDocument(documentId, announcedAt, facts);
    }
}
