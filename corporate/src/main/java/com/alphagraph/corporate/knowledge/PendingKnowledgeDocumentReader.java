package com.alphagraph.corporate.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** One document ready for canonical knowledge extraction - the input to {@link DocumentIntelligenceEngine}. */
record PendingKnowledgeDocument(UUID id, String symbol, String extractedText) {
}

/** Reads documents that have finished Module 2.1's text/chunk/embed pipeline but not yet had canonical knowledge extracted. */
@Component
class PendingKnowledgeDocumentReader {

    private final JdbcTemplate jdbcTemplate;

    PendingKnowledgeDocumentReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<PendingKnowledgeDocument> findProcessed() {
        return jdbcTemplate.query(
            "SELECT id, symbol, extracted_text FROM corporate.documents WHERE status = 'PROCESSED'",
            (rs, rowNum) -> new PendingKnowledgeDocument(
                (UUID) rs.getObject("id"), rs.getString("symbol"), rs.getString("extracted_text")
            )
        );
    }
}
