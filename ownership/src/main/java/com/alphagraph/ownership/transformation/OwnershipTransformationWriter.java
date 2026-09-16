package com.alphagraph.ownership.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Upserts one {@code (instrument_id, as_of_date)} row - "latest classification per instrument per
 * day", same convention {@code ownership.interpretation.InstitutionalInterpretationWriter} uses for
 * {@code institutional_interpretations}. Reasons are deleted and reinserted every time, matching
 * the parent's upsert semantics.
 */
@Component
class OwnershipTransformationWriter {

    private final JdbcTemplate jdbcTemplate;

    OwnershipTransformationWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(OwnershipTransformationResult result) {
        UUID stateId = jdbcTemplate.query(
            """
            INSERT INTO ownership.transformation_states (
                id, instrument_id, symbol, as_of_date, latest_period_end, prior_period_end,
                transformation_state, confidence, rule_version, computed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                symbol = EXCLUDED.symbol, latest_period_end = EXCLUDED.latest_period_end,
                prior_period_end = EXCLUDED.prior_period_end, transformation_state = EXCLUDED.transformation_state,
                confidence = EXCLUDED.confidence, rule_version = EXCLUDED.rule_version, computed_at = EXCLUDED.computed_at
            RETURNING id
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            UUID.randomUUID(), result.instrumentId(), result.symbol(), Date.valueOf(result.asOfDate()),
            Date.valueOf(result.latestPeriodEnd()), result.priorPeriodEnd() == null ? null : Date.valueOf(result.priorPeriodEnd()),
            result.primaryState().name(), result.confidence(), result.ruleVersion(), Timestamp.from(result.computedAt())
        ).get(0);

        jdbcTemplate.update("DELETE FROM ownership.transformation_state_reasons WHERE state_id = ?", stateId);
        for (ReasonCode reason : result.reasons()) {
            jdbcTemplate.update(
                "INSERT INTO ownership.transformation_state_reasons (id, state_id, reason_code, metric_value, evidence_reference) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), stateId, reason.code(), reason.metricValue(), reason.evidenceReference()
            );
        }
    }
}
