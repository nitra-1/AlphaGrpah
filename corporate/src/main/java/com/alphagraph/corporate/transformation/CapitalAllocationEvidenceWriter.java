package com.alphagraph.corporate.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.UUID;

/**
 * Appends one row to {@code corporate.transformation_evidence} - never updated in place.
 * {@code ON CONFLICT (instrument_id, metric_name, as_of_date) DO NOTHING} makes this safe to
 * rerun the same day, same convention as {@code ownership.transformation.TransformationEvidenceWriter}.
 */
@Component
class CapitalAllocationEvidenceWriter {

    private static final String SOURCE = "CORPORATE_ACTIONS";
    private static final int RULE_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;

    CapitalAllocationEvidenceWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(CapitalAllocationEvidenceObservation observation) {
        jdbcTemplate.update(
            """
            INSERT INTO corporate.transformation_evidence (
                id, instrument_id, symbol, metric_name, as_of_date, prior_as_of_date, value, prior_value,
                change, velocity_per_day, persistence_days, confidence, window_days, source, rule_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, metric_name, as_of_date) DO NOTHING
            """,
            UUID.randomUUID(), observation.instrumentId(), observation.symbol(), observation.metric().name(),
            Date.valueOf(observation.asOfDate()), Date.valueOf(observation.priorAsOfDate()), observation.value(), observation.priorValue(),
            observation.change(), observation.velocityPerDay(), observation.persistenceDays(), observation.confidence(),
            observation.windowDays(), SOURCE, RULE_VERSION
        );
    }
}
