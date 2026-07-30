package com.alphagraph.corporate.processing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** No natural key to upsert on - a re-run reprocessing the same document would duplicate entities; acceptable for Module 2.1 since reprocessing isn't triggered anywhere yet (see DocumentProcessingOrchestrator). */
@Component
public class DocumentEntityWriter {

    private final JdbcTemplate jdbcTemplate;

    public DocumentEntityWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void write(UUID documentId, UUID chunkId, String entityText, String entityType) {
        jdbcTemplate.update(
            "INSERT INTO corporate.document_entities (id, document_id, chunk_id, entity_text, entity_type) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), documentId, chunkId, entityText, entityType
        );
    }
}
