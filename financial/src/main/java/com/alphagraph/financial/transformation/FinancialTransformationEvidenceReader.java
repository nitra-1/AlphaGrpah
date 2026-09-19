package com.alphagraph.financial.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads {@code financial.transformation_evidence} back - Stage 1's own writer was write-only
 * until Stage 2 needed to read it.
 */
@Component
class FinancialTransformationEvidenceReader {

    private static final String SELECT_COLUMNS = """
        instrument_id, symbol, metric_name, period_end, prior_period_end, value, prior_value,
        change, velocity_per_quarter, persistence_quarters, confidence, comparator_used
        """;

    private final JdbcTemplate jdbcTemplate;

    FinancialTransformationEvidenceReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<FinancialEvidenceObservation> findLatest(UUID instrumentId, FinancialMetric metric) {
        List<FinancialEvidenceObservation> rows = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM financial.transformation_evidence " +
            "WHERE instrument_id = ? AND metric_name = ? ORDER BY period_end DESC LIMIT 1",
            ROW_MAPPER, instrumentId, metric.name()
        );
        return rows.stream().findFirst();
    }

    /** Ascending by period_end, most recent {@code limit} rows. */
    List<FinancialEvidenceObservation> findRecent(UUID instrumentId, FinancialMetric metric, int limit) {
        List<FinancialEvidenceObservation> descending = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM financial.transformation_evidence " +
            "WHERE instrument_id = ? AND metric_name = ? ORDER BY period_end DESC LIMIT ?",
            ROW_MAPPER, instrumentId, metric.name(), limit
        );
        List<FinancialEvidenceObservation> ascending = new ArrayList<>(descending);
        Collections.reverse(ascending);
        return ascending;
    }

    /** Every instrument with at least one real evidence row - Stage 2's own driving universe. */
    List<UUID> findAllInstrumentIds() {
        return jdbcTemplate.query(
            "SELECT DISTINCT instrument_id FROM financial.transformation_evidence",
            (rs, rowNum) -> (UUID) rs.getObject("instrument_id")
        );
    }

    private static final RowMapper<FinancialEvidenceObservation> ROW_MAPPER = (rs, rowNum) -> new FinancialEvidenceObservation(
        FinancialMetric.valueOf(rs.getString("metric_name")), (UUID) rs.getObject("instrument_id"), rs.getString("symbol"),
        rs.getDate("period_end").toLocalDate(), rs.getDate("prior_period_end") == null ? null : rs.getDate("prior_period_end").toLocalDate(),
        rs.getBigDecimal("value"), rs.getBigDecimal("prior_value"), rs.getBigDecimal("change"), rs.getBigDecimal("velocity_per_quarter"),
        rs.getInt("persistence_quarters"), rs.getDouble("confidence"), rs.getString("comparator_used")
    );
}
