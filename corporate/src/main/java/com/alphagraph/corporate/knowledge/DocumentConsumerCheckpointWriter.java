package com.alphagraph.corporate.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Marks a document as processed by one downstream rule engine, in
 * {@code corporate.document_consumer_checkpoints}. Shared across every consumer (Corporate Event
 * Engine, Order Book Engine, future engines) - each names itself via a stable {@code consumer}
 * string (e.g. "CORPORATE_EVENT_ENGINE") and calls this after processing a document, regardless of
 * whether it produced any output. Each consumer's own reader is responsible for filtering out
 * already-checkpointed documents on its own read query (a single anti-join is more efficient than
 * checking one document at a time here).
 */
@Component
public class DocumentConsumerCheckpointWriter {

    private final JdbcTemplate jdbcTemplate;

    public DocumentConsumerCheckpointWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void markProcessed(UUID documentId, String consumer) {
        jdbcTemplate.update(
            "INSERT INTO corporate.document_consumer_checkpoints (document_id, consumer) VALUES (?, ?) ON CONFLICT DO NOTHING",
            documentId, consumer
        );
    }
}
