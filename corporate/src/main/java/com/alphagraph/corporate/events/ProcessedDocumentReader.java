package com.alphagraph.corporate.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** One document ready for event extraction - the input to {@link CorporateEventEngine} via {@link EventExtractionOrchestrator}. */
record ProcessedDocument(UUID id, UUID instrumentId, String symbol, String extractedText) {
}

/** Reads documents that have finished the Document Pipeline but not yet had events extracted. */
@Component
class ProcessedDocumentReader {

    private final JdbcTemplate jdbcTemplate;

    ProcessedDocumentReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<ProcessedDocument> findProcessed() {
        return jdbcTemplate.query(
            "SELECT id, instrument_id, symbol, extracted_text FROM corporate.documents WHERE status = 'PROCESSED'",
            (rs, rowNum) -> new ProcessedDocument(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("instrument_id"),
                rs.getString("symbol"), rs.getString("extracted_text")
            )
        );
    }
}
