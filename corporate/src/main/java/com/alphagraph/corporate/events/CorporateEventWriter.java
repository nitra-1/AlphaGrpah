package com.alphagraph.corporate.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Persists one {@link ExtractedEvent} as a {@code corporate.corporate_events} row. No upsert key
 * here (unlike {@code DocumentChunkWriter}'s (document_id, chunk_index)) - a document is only ever
 * extracted once per {@link EventExtractionOrchestrator}'s PROCESSED -> EVENTS_EXTRACTED status
 * transition, so idempotency comes from that status guard, not a unique constraint on this table.
 *
 * {@code raw_response} is cast via {@code ::jsonb} in the SQL text itself rather than bound with
 * {@code java.sql.Types.OTHER} or a driver-specific {@code PGobject} - this module deliberately
 * has no compile-time dependency on {@code org.postgresql:postgresql} (see the V2 migration's
 * pgvector note), so the text-to-jsonb cast has to happen in SQL, not in JDBC binding code.
 */
@Component
class CorporateEventWriter {

    private final JdbcTemplate jdbcTemplate;

    CorporateEventWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(UUID documentId, UUID instrumentId, String symbol, ExtractedEvent event, int promptVersion, Instant extractedAt) {
        jdbcTemplate.update(
            """
            INSERT INTO corporate.corporate_events (
                id, document_id, instrument_id, symbol, event_type, category, summary,
                confidence, expected_duration, revenue_impact, signal, prompt_version,
                raw_response, extracted_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """,
            UUID.randomUUID(), documentId, instrumentId, symbol,
            event.eventType().name(), event.category(), event.summary(),
            event.confidence(), event.expectedDuration(), event.revenueImpact().name(), event.signal().name(),
            promptVersion, event.rawResponse(), Timestamp.from(extractedAt)
        );
    }
}
