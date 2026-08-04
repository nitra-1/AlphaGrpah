package com.alphagraph.corporate.commentary;

import com.alphagraph.corporate.api.DocumentFact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One document ready for management-observation parsing, with its canonical facts and announcement time already loaded. */
record PendingManagementDocument(UUID id, UUID instrumentId, String symbol, Instant announcedAt, List<DocumentFact> facts) {
}

/**
 * Reads documents in KNOWLEDGE_EXTRACTED status without a {@link ManagementObservationReader#CONSUMER}
 * checkpoint yet - the same checkpoint-based idempotency pattern as
 * {@code corporate.orderbook.PendingOrderDocumentReader}, since this engine independently consumes
 * the same shared canonical output.
 */
@Component
class PendingManagementDocumentReader {

    private final JdbcTemplate jdbcTemplate;

    PendingManagementDocumentReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<PendingManagementDocument> findUnprocessed() {
        List<UUID> documentIds = jdbcTemplate.query(
            """
            SELECT d.id
            FROM corporate.documents d
            WHERE d.status = 'KNOWLEDGE_EXTRACTED'
              AND NOT EXISTS (
                  SELECT 1 FROM corporate.document_consumer_checkpoints c
                  WHERE c.document_id = d.id AND c.consumer = ?
              )
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            ManagementObservationReader.CONSUMER
        );

        return documentIds.stream().map(this::loadDocument).toList();
    }

    private PendingManagementDocument loadDocument(UUID documentId) {
        var docRow = jdbcTemplate.queryForMap(
            "SELECT instrument_id, symbol, announced_at FROM corporate.documents WHERE id = ?", documentId
        );

        List<DocumentFact> facts = jdbcTemplate.query(
            "SELECT id, fact_type, fact_value, unit, confidence, created_at, commitment_level, fact_group FROM corporate.document_facts WHERE document_id = ? AND fact_group IS NOT NULL",
            (rs, rowNum) -> new DocumentFact(
                (UUID) rs.getObject("id"), documentId, rs.getString("fact_type"), rs.getString("fact_value"),
                rs.getString("unit"), rs.getDouble("confidence"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("commitment_level"), (UUID) rs.getObject("fact_group")
            ),
            documentId
        );

        return new PendingManagementDocument(
            documentId, (UUID) docRow.get("instrument_id"), (String) docRow.get("symbol"),
            ((java.sql.Timestamp) docRow.get("announced_at")).toInstant(), facts
        );
    }
}
