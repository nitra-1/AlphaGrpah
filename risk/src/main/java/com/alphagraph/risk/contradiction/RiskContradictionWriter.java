package com.alphagraph.risk.contradiction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Upserts one {@code (instrument_id, as_of_date)} row - "latest classification per instrument per
 * day", same convention every other family's Stage 2 writer uses. Reasons are deleted and
 * reinserted every time, matching the parent's upsert semantics. Takes plain scalar parameters
 * (plus the small {@link ReasonEntry} record for the variable-length reasons list) rather than a
 * shared type - the only caller, {@code intelligence.riskcontradiction}, has its own state/result
 * shapes, and there is no other consumer to justify a new shared cross-module type for this alone
 * (same reasoning {@code sector.transformation.SectorContextEvidenceWriter}'s own javadoc gives).
 */
@Component
public class RiskContradictionWriter {

    private static final int RULE_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;

    public RiskContradictionWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void write(
        UUID instrumentId, String symbol, LocalDate asOfDate, String primaryState,
        int evidenceCoveragePct, String dataReadiness, double confidence, List<ReasonEntry> reasons
    ) {
        UUID stateId = jdbcTemplate.query(
            """
            INSERT INTO risk.contradiction_states (
                id, instrument_id, symbol, as_of_date, primary_state, evidence_coverage_pct,
                data_readiness, confidence, rule_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                symbol = EXCLUDED.symbol, primary_state = EXCLUDED.primary_state,
                evidence_coverage_pct = EXCLUDED.evidence_coverage_pct, data_readiness = EXCLUDED.data_readiness,
                confidence = EXCLUDED.confidence, rule_version = EXCLUDED.rule_version
            RETURNING id
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            UUID.randomUUID(), instrumentId, symbol, Date.valueOf(asOfDate), primaryState,
            evidenceCoveragePct, dataReadiness, confidence, RULE_VERSION
        ).get(0);

        jdbcTemplate.update("DELETE FROM risk.contradiction_state_reasons WHERE state_id = ?", stateId);
        for (ReasonEntry reason : reasons) {
            jdbcTemplate.update(
                "INSERT INTO risk.contradiction_state_reasons (id, state_id, reason_code, metric_value, evidence_reference) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), stateId, reason.code(), reason.metricValue(), reason.evidenceReference()
            );
        }
    }

    public record ReasonEntry(String code, Double metricValue, String evidenceReference) {
    }
}
