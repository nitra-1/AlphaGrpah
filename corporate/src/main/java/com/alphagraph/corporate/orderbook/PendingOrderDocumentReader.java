package com.alphagraph.corporate.orderbook;

import com.alphagraph.corporate.api.DocumentFact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** One document ready for order-fact parsing, with its canonical facts already loaded. */
record PendingOrderDocument(UUID id, UUID instrumentId, String symbol, List<DocumentFact> facts) {
}

/**
 * Reads documents in KNOWLEDGE_EXTRACTED status without an {@link OrderBookLedgerReader#CONSUMER}
 * checkpoint yet - the same checkpoint-based idempotency pattern as
 * {@code corporate.events.KnowledgeDocumentReader}, since both engines independently consume the
 * same shared canonical output.
 */
@Component
class PendingOrderDocumentReader {

    private final JdbcTemplate jdbcTemplate;

    PendingOrderDocumentReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<PendingOrderDocument> findUnprocessed() {
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
            OrderBookLedgerReader.CONSUMER
        );

        return documentIds.stream().map(this::loadDocument).toList();
    }

    private PendingOrderDocument loadDocument(UUID documentId) {
        var docRow = jdbcTemplate.queryForMap(
            "SELECT instrument_id, symbol FROM corporate.documents WHERE id = ?", documentId
        );

        List<DocumentFact> facts = jdbcTemplate.query(
            "SELECT id, fact_type, fact_value, unit, confidence, created_at FROM corporate.document_facts WHERE document_id = ?",
            (rs, rowNum) -> new DocumentFact(
                (UUID) rs.getObject("id"), documentId, rs.getString("fact_type"), rs.getString("fact_value"),
                rs.getString("unit"), rs.getDouble("confidence"), rs.getTimestamp("created_at").toInstant()
            ),
            documentId
        );

        return new PendingOrderDocument(documentId, (UUID) docRow.get("instrument_id"), (String) docRow.get("symbol"), facts);
    }
}
