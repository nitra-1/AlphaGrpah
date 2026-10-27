package com.alphagraph.corporate.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Recognises an announcement whose attachment text is identical to one already extracted for the
 * same symbol. One exchange filing is routinely attached to several separate announcements
 * ("results", "record date", "board outcome"), and each used to be stored and sent to Claude in
 * full - on 24 Oct 46% of the day's text was such a repeat. The repeat is marked extracted without
 * an LLM call and linked to the document that was extracted; it deliberately gets no facts, so the
 * same filing is also no longer counted once per announcement downstream.
 *
 * <p>Only a document that was itself fully extracted ({@code KNOWLEDGE_EXTRACTED}, and not itself a
 * repeat) counts as the original, so a document that failed or was deferred never causes its twin to
 * be skipped - the twin just extracts normally and the deferred one becomes the repeat later.
 */
@Component
class DocumentRepeatDetector {

    private final JdbcTemplate jdbcTemplate;

    DocumentRepeatDetector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<UUID> findExtractedOriginal(UUID documentId, String source, UUID instrumentId, String textHash) {
        List<UUID> originals = jdbcTemplate.query(
            """
            SELECT id FROM corporate.documents
            WHERE id <> ? AND source = ? AND instrument_id IS NOT DISTINCT FROM ? AND text_hash = ?
              AND status = 'KNOWLEDGE_EXTRACTED' AND duplicate_of_document_id IS NULL
            ORDER BY announced_at LIMIT 1
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            documentId, source, instrumentId, textHash
        );
        return originals.stream().findFirst();
    }

    void markRepeat(UUID documentId, String textHash, UUID originalId) {
        jdbcTemplate.update(
            "UPDATE corporate.documents SET status = 'KNOWLEDGE_EXTRACTED', text_hash = ?, duplicate_of_document_id = ? WHERE id = ?",
            textHash, originalId, documentId
        );
    }
}
