package com.alphagraph.ownership.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.UUID;

/**
 * Appends one row to {@code ownership.transformation_evidence} - never updated in place.
 * {@code ON CONFLICT (instrument_id, metric_name, period_end) DO NOTHING} makes this safe to
 * rerun the same day (the engine recomputes every run regardless of whether new data arrived,
 * matching {@code institutional_interpretations}' daily-refresh convention) without ever
 * duplicating or overwriting an already-evidenced quarter transition - still append-only, just
 * idempotent-safe.
 */
@Component
class TransformationEvidenceWriter {

    private static final String SOURCE = "NSE_SHAREHOLDING_XBRL";

    private final JdbcTemplate jdbcTemplate;

    TransformationEvidenceWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(EvidenceObservation observation, int ruleVersion) {
        jdbcTemplate.update(
            """
            INSERT INTO ownership.transformation_evidence (
                id, instrument_id, symbol, metric_name, period_end, prior_period_end, value, prior_value,
                change_pp, velocity_pp_per_quarter, persistence_quarters, confidence, source, rule_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, metric_name, period_end) DO NOTHING
            """,
            UUID.randomUUID(), observation.instrumentId(), observation.symbol(), observation.metric().name(),
            Date.valueOf(observation.periodEnd()), observation.priorPeriodEnd() == null ? null : Date.valueOf(observation.priorPeriodEnd()),
            observation.value(), observation.priorValue(), observation.changePp(), observation.velocityPpPerQuarter(),
            observation.persistenceQuarters(), observation.confidence(), SOURCE, ruleVersion
        );
    }
}
