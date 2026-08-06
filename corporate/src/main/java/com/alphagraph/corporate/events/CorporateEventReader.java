package com.alphagraph.corporate.events;

import com.alphagraph.corporate.api.CorporateEvent;
import com.alphagraph.corporate.api.EventSignal;
import com.alphagraph.corporate.api.EventType;
import com.alphagraph.corporate.api.RevenueImpact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads an instrument's recent {@link CorporateEvent}s - added in Module 2.8 (Corporate Signal
 * Engine), the first consumer to need one ({@link CorporateEventWriter} was write-only until now).
 * Unlike the Score-implementing engines' single "latest snapshot" reads, corporate_events is
 * append-only with no as_of_date, so this reads every event within a lookback window rather than
 * one row.
 */
@Component
public class CorporateEventReader {

    private final JdbcTemplate jdbcTemplate;

    public CorporateEventReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CorporateEvent> findRecentByInstrument(UUID instrumentId, int lookbackDays) {
        Instant since = Instant.now().minusSeconds(lookbackDays * 86400L);
        return jdbcTemplate.query(
            """
            SELECT id, document_id, instrument_id, symbol, event_type, category, summary, confidence,
                   expected_duration, revenue_impact, signal, prompt_version, extracted_at
            FROM corporate.corporate_events WHERE instrument_id = ? AND extracted_at >= ?
            ORDER BY extracted_at DESC
            """,
            (rs, rowNum) -> new CorporateEvent(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("document_id"), (UUID) rs.getObject("instrument_id"),
                rs.getString("symbol"), EventType.valueOf(rs.getString("event_type")), rs.getString("category"),
                rs.getString("summary"), rs.getDouble("confidence"), rs.getString("expected_duration"),
                RevenueImpact.valueOf(rs.getString("revenue_impact")), EventSignal.valueOf(rs.getString("signal")),
                rs.getInt("prompt_version"), rs.getTimestamp("extracted_at").toInstant()
            ),
            instrumentId, Timestamp.from(since)
        );
    }
}
