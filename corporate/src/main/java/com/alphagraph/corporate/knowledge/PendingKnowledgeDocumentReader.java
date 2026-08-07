package com.alphagraph.corporate.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** One document ready for canonical knowledge extraction - the input to {@link DocumentIntelligenceEngine}. */
record PendingKnowledgeDocument(UUID id, UUID instrumentId, String symbol, String extractedText) {
}

/** Reads documents that have finished Module 2.1's text/chunk/embed pipeline but not yet had canonical knowledge extracted. */
@Component
class PendingKnowledgeDocumentReader {

    private static final RowMapper<PendingKnowledgeDocument> ROW_MAPPER = (rs, rowNum) -> new PendingKnowledgeDocument(
        (UUID) rs.getObject("id"), (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getString("extracted_text")
    );

    private final JdbcTemplate jdbcTemplate;

    PendingKnowledgeDocumentReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<PendingKnowledgeDocument> findProcessed() {
        return jdbcTemplate.query(
            "SELECT id, instrument_id, symbol, extracted_text FROM corporate.documents WHERE status = 'PROCESSED'",
            ROW_MAPPER
        );
    }

    /** Looks up a single document regardless of status - used by the admin news-review "keep" path (Module 2.6 pre-filter retrofit), which extracts immediately rather than waiting for the next scheduled PROCESSED-only run. */
    Optional<PendingKnowledgeDocument> findById(UUID id) {
        List<PendingKnowledgeDocument> rows = jdbcTemplate.query(
            "SELECT id, instrument_id, symbol, extracted_text FROM corporate.documents WHERE id = ?",
            ROW_MAPPER, id
        );
        return rows.stream().findFirst();
    }
}
