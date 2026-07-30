package com.alphagraph.corporate.processing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

record PendingDocument(UUID id, byte[] rawPdf, String symbol, String externalId) {
}

/** Reads documents that have been downloaded but not yet processed - the input to {@link DocumentProcessingOrchestrator}. */
@Component
class PendingDocumentReader {

    private final JdbcTemplate jdbcTemplate;

    PendingDocumentReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<PendingDocument> findDownloaded() {
        return jdbcTemplate.query(
            "SELECT id, raw_pdf, symbol, external_id FROM corporate.documents WHERE status = 'DOWNLOADED'",
            (rs, rowNum) -> new PendingDocument(
                (UUID) rs.getObject("id"), rs.getBytes("raw_pdf"), rs.getString("symbol"), rs.getString("external_id")
            )
        );
    }
}
