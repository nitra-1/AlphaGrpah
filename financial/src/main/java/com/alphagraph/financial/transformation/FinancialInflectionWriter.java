package com.alphagraph.financial.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.UUID;

/**
 * Upserts one {@code (instrument_id, as_of_date)} row - "latest classification per instrument per
 * real quarter" (not per calendar day - see {@code FinancialInflectionResult}'s javadoc). Reasons
 * are deleted and reinserted every time, matching every other family's exact convention.
 */
@Component
class FinancialInflectionWriter {

    private static final int RULE_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;

    FinancialInflectionWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(FinancialInflectionResult result) {
        UUID stateId = jdbcTemplate.query(
            """
            INSERT INTO financial.inflection_states (
                id, instrument_id, symbol, as_of_date, primary_state, driving_metric, level, change,
                velocity_band, persistence, confidence, rule_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                symbol = EXCLUDED.symbol, primary_state = EXCLUDED.primary_state, driving_metric = EXCLUDED.driving_metric,
                level = EXCLUDED.level, change = EXCLUDED.change, velocity_band = EXCLUDED.velocity_band,
                persistence = EXCLUDED.persistence, confidence = EXCLUDED.confidence, rule_version = EXCLUDED.rule_version
            RETURNING id
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            UUID.randomUUID(), result.instrumentId(), result.symbol(), Date.valueOf(result.asOfDate()),
            result.primaryState().name(), result.drivingMetric() == null ? null : result.drivingMetric().name(),
            result.level(), result.change(), result.velocityBand() == null ? null : result.velocityBand().name(),
            result.persistence(), result.confidence(), RULE_VERSION
        ).get(0);

        jdbcTemplate.update("DELETE FROM financial.inflection_state_reasons WHERE state_id = ?", stateId);
        for (ReasonCode reason : result.reasons()) {
            jdbcTemplate.update(
                "INSERT INTO financial.inflection_state_reasons (id, state_id, reason_code, metric_value, evidence_reference) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), stateId, reason.code(), reason.metricValue(), reason.evidenceReference()
            );
        }
    }
}
