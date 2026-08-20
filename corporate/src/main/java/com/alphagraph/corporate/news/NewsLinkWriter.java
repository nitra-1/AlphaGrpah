package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.NewsInstrumentLink;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * Persists one {@link NewsInstrumentLink} (Layer 1) as a {@code corporate.document_instrument_links}
 * row. {@code ON CONFLICT (document_id, instrument_id) DO NOTHING} - a document naming the same
 * instrument twice is treated as the same real-world impact, not two; this also makes it safe to
 * reprocess a document whose {@code document_consumer_checkpoints} row was reset (e.g. after a
 * previously-unmatched company becomes matchable), since links that already succeeded the first
 * time won't be duplicated.
 */
@Component
class NewsLinkWriter {

    private final JdbcTemplate jdbcTemplate;

    NewsLinkWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(NewsInstrumentLink link) {
        jdbcTemplate.update(
            """
            INSERT INTO corporate.document_instrument_links (
                id, document_id, instrument_id, symbol, direction, signal, impact_summary, extraction_confidence, announced_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (document_id, instrument_id) DO NOTHING
            """,
            link.id(), link.documentId(), link.instrumentId(), link.symbol(), link.direction().name(),
            link.signal(), link.impactSummary(), link.extractionConfidence(), Timestamp.from(link.announcedAt())
        );
    }
}
