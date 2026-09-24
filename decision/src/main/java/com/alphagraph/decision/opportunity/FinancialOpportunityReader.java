package com.alphagraph.decision.opportunity;

import com.alphagraph.decision.api.EvidenceObservation;
import com.alphagraph.decision.api.InflectionState;
import com.alphagraph.decision.api.ReasonNote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * decision's own raw-SQL reader over FINANCIAL's package-private Stage 1-3 tables - {@code
 * financial.transformation} is entirely package-private (confirmed by direct read, and it has no
 * reader at all for its own Stage 3 tables), so this mirrors the exact "own copy, schema-qualified
 * SQL, no cross-module import" pattern {@code discovery.convergence}'s own sequence readers
 * already established one layer down. Every query is bounded {@code <= asOfDate}, never an
 * unconditional latest.
 */
@Component
class FinancialOpportunityReader extends AbstractDomainSequenceReader {

    private final JdbcTemplate jdbcTemplate;

    FinancialOpportunityReader(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, "financial");
        this.jdbcTemplate = jdbcTemplate;
    }

    /** One row per {@code metric_name}, each the most recent as of {@code asOfDate} (financial's evidence date column is {@code period_end}). */
    List<EvidenceObservation> findLatestEvidence(UUID instrumentId, LocalDate asOfDate) {
        return jdbcTemplate.query(
            """
            SELECT DISTINCT ON (metric_name) metric_name, period_end, value, prior_value, change, confidence, source
            FROM financial.transformation_evidence WHERE instrument_id = ? AND period_end <= ? ORDER BY metric_name, period_end DESC
            """,
            (rs, rowNum) -> new EvidenceObservation(
                rs.getString("metric_name"), rs.getDate("period_end").toLocalDate(),
                toDouble(rs.getObject("value")), toDouble(rs.getObject("prior_value")), toDouble(rs.getObject("change")),
                toDouble(rs.getObject("confidence")), rs.getString("source")
            ),
            instrumentId, Date.valueOf(asOfDate)
        );
    }

    Optional<InflectionState> findLatestInflection(UUID instrumentId, LocalDate asOfDate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, primary_state, driving_metric, level, change, velocity_band, persistence, confidence, as_of_date
            FROM financial.inflection_states WHERE instrument_id = ? AND as_of_date <= ? ORDER BY as_of_date DESC LIMIT 1
            """,
            instrumentId, Date.valueOf(asOfDate)
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        List<ReasonNote> reasons = jdbcTemplate.query(
            "SELECT reason_code, metric_value, evidence_reference FROM financial.inflection_state_reasons WHERE state_id = ?",
            (rs, rowNum) -> new ReasonNote(rs.getString("reason_code"), null, toDouble(rs.getObject("metric_value")), null, rs.getString("evidence_reference")),
            row.get("id")
        );
        return Optional.of(new InflectionState(
            (String) row.get("primary_state"), (String) row.get("driving_metric"), toDouble(row.get("level")), toDouble(row.get("change")),
            (String) row.get("velocity_band"), (Integer) row.get("persistence"), toDouble(row.get("confidence")),
            ((Date) row.get("as_of_date")).toLocalDate(), reasons
        ));
    }

    private static Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }
}
