package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.DocumentFact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

record PendingNewsDocument(UUID id, String title, Instant announcedAt, List<DocumentFact> facts) {
}

/**
 * Reads KNOWLEDGE_EXTRACTED documents not yet checkpointed by the News Catalyst Engine - the
 * input to {@link NewsLinkParser}. Unlike {@code corporate.commentary.
 * PendingManagementDocumentReader}, this does NOT read {@code instrument_id}/{@code symbol} off
 * the document row - a news document's own instrument_id is NULL (Module 2.6's whole premise),
 * so every affected instrument comes exclusively from {@link NewsLinkParser} resolving each
 * fact_group's companyName, not from the document itself.
 */
@Component
class PendingNewsDocumentReader {

    private final JdbcTemplate jdbcTemplate;

    PendingNewsDocumentReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<PendingNewsDocument> findUnprocessed() {
        List<UUID> documentIds = jdbcTemplate.query(
            """
            SELECT d.id FROM corporate.documents d
            WHERE d.status = 'KNOWLEDGE_EXTRACTED'
              AND NOT EXISTS (
                  SELECT 1 FROM corporate.document_consumer_checkpoints c
                  WHERE c.document_id = d.id AND c.consumer = ?
              )
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            NewsLinkReader.CONSUMER
        );

        List<PendingNewsDocument> documents = new ArrayList<>();
        for (UUID documentId : documentIds) {
            documents.add(readOne(documentId));
        }
        return documents;
    }

    private PendingNewsDocument readOne(UUID documentId) {
        var docRow = jdbcTemplate.queryForMap(
            "SELECT title, announced_at FROM corporate.documents WHERE id = ?", documentId
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

        return new PendingNewsDocument(
            documentId, (String) docRow.get("title"), ((java.sql.Timestamp) docRow.get("announced_at")).toInstant(), facts
        );
    }
}
