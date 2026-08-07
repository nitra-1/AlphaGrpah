package com.alphagraph.decision.analyst;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Persists a freshly-generated explanation - one row per (instrument, type, day), upserting on a same-day re-run, mirroring decision.report.DailyReportStore. */
@Component
class AnalystExplanationStore {

    private final JdbcTemplate jdbcTemplate;

    AnalystExplanationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(UUID instrumentId, String explanationType, LocalDate businessDate, String explanation) {
        jdbcTemplate.update(
            """
            INSERT INTO decision.analyst_explanations (id, instrument_id, explanation_type, business_date, explanation, generated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, explanation_type, business_date) DO UPDATE SET
                explanation = EXCLUDED.explanation,
                generated_at = EXCLUDED.generated_at
            """,
            UUID.randomUUID(), instrumentId, explanationType, Date.valueOf(businessDate), explanation, Timestamp.from(Instant.now())
        );
    }
}
