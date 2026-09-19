package com.alphagraph.sector.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.UUID;

/**
 * Upserts one {@code (instrument_id, as_of_date)} row - "latest classification per instrument per
 * real evidence date", same convention {@code market.transformation.MarketInflectionWriter} uses.
 * Reasons are deleted and reinserted every time, matching the parent's upsert semantics.
 */
@Component
class SectorInflectionWriter {

    private static final int RULE_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;

    SectorInflectionWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(SectorInflectionResult result) {
        UUID stateId = jdbcTemplate.query(
            """
            INSERT INTO sector.inflection_states (
                id, instrument_id, symbol, as_of_date, primary_state, driving_metric, level, change,
                velocity_band, persistence, evidence_coverage_pct, data_readiness, confidence, rule_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                symbol = EXCLUDED.symbol, primary_state = EXCLUDED.primary_state, driving_metric = EXCLUDED.driving_metric,
                level = EXCLUDED.level, change = EXCLUDED.change, velocity_band = EXCLUDED.velocity_band,
                persistence = EXCLUDED.persistence, evidence_coverage_pct = EXCLUDED.evidence_coverage_pct,
                data_readiness = EXCLUDED.data_readiness, confidence = EXCLUDED.confidence, rule_version = EXCLUDED.rule_version
            RETURNING id
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            UUID.randomUUID(), result.instrumentId(), result.symbol(), Date.valueOf(result.asOfDate()),
            result.primaryState().name(), result.drivingMetric() == null ? null : result.drivingMetric().name(),
            result.level(), result.change(), result.velocityBand() == null ? null : result.velocityBand().name(),
            result.persistence(), result.evidenceCoveragePct(), result.dataReadiness().name(), result.confidence(), RULE_VERSION
        ).get(0);

        jdbcTemplate.update("DELETE FROM sector.inflection_state_reasons WHERE state_id = ?", stateId);
        for (ReasonCode reason : result.reasons()) {
            jdbcTemplate.update(
                "INSERT INTO sector.inflection_state_reasons (id, state_id, reason_code, metric_value, evidence_reference) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), stateId, reason.code(), reason.metricValue(), reason.evidenceReference()
            );
        }
    }
}
