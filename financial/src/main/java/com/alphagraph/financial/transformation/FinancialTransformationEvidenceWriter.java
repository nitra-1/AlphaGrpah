package com.alphagraph.financial.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.UUID;

/**
 * Appends one row to {@code financial.transformation_evidence} - never updated in place.
 * {@code ON CONFLICT (instrument_id, metric_name, period_end) DO NOTHING} makes this safe to
 * rerun the same day, same convention as {@code ownership.transformation.TransformationEvidenceWriter}.
 */
@Component
class FinancialTransformationEvidenceWriter {

    private static final String SOURCE = "NSE_RESULTS_COMPARISION";
    private static final int RULE_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;

    FinancialTransformationEvidenceWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(FinancialEvidenceObservation observation) {
        jdbcTemplate.update(
            """
            INSERT INTO financial.transformation_evidence (
                id, instrument_id, symbol, metric_name, period_end, prior_period_end, value, prior_value,
                change, velocity_per_quarter, persistence_quarters, confidence, comparator_used, source, rule_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, metric_name, period_end) DO NOTHING
            """,
            UUID.randomUUID(), observation.instrumentId(), observation.symbol(), observation.metric().name(),
            Date.valueOf(observation.periodEnd()), observation.priorPeriodEnd() == null ? null : Date.valueOf(observation.priorPeriodEnd()),
            observation.value(), observation.priorValue(), observation.change(), observation.velocityPerQuarter(),
            observation.persistenceQuarters(), observation.confidence(), observation.comparatorUsed(), SOURCE, RULE_VERSION
        );
    }
}
