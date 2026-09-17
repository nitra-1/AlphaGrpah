package com.alphagraph.sector.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Appends one row to {@code sector.transformation_evidence} - never updated in place.
 * {@code ON CONFLICT (instrument_id, metric_name, as_of_date) DO NOTHING} makes this safe to
 * rerun the same day, same convention as {@code ownership.transformation.TransformationEvidenceWriter}.
 * Takes plain scalar parameters rather than a shared record type - the only caller,
 * {@code intelligence.sectorcontext}, has its own metric enum and observation shape, and there is
 * no other consumer to justify a new public cross-module type for this alone.
 */
@Component
public class SectorContextEvidenceWriter {

    private static final String SOURCE = "SECTOR_CONTEXT";
    private static final int RULE_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;

    public SectorContextEvidenceWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void write(
        UUID instrumentId, String symbol, String metricName, LocalDate asOfDate, LocalDate priorAsOfDate,
        BigDecimal value, BigDecimal priorValue, BigDecimal change, int persistenceDays, double confidence
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO sector.transformation_evidence (
                id, instrument_id, symbol, metric_name, as_of_date, prior_as_of_date, value, prior_value,
                change, persistence_days, confidence, source, rule_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, metric_name, as_of_date) DO NOTHING
            """,
            UUID.randomUUID(), instrumentId, symbol, metricName, Date.valueOf(asOfDate),
            priorAsOfDate == null ? null : Date.valueOf(priorAsOfDate), value, priorValue, change,
            persistenceDays, confidence, SOURCE, RULE_VERSION
        );
    }
}
