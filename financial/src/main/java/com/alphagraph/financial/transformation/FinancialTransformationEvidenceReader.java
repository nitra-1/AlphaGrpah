package com.alphagraph.financial.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
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

    /**
     * Added for Stage 3 sequence detection (docs/008 §16's point-in-time rule): the most recent
     * real prefix ending at {@code upToInclusiveOrNull}, ascending. Always
     * {@code ORDER BY period_end DESC LIMIT ?} first, then reversed in Java - never fetch the
     * oldest {@code limit} rows and truncate, which would silently drop the real end of the window
     * for a deep-history instrument. {@code upToInclusiveOrNull == null} means "latest" (today's
     * live run); a real historical date makes point-in-time backfill correct by construction, since
     * the query itself can never see a row after the bound.
     */
    List<FinancialEvidenceObservation> findHistory(UUID instrumentId, FinancialMetric metric, LocalDate upToInclusiveOrNull, int limit) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM financial.transformation_evidence WHERE instrument_id = ? AND metric_name = ?"
            + (upToInclusiveOrNull == null ? "" : " AND period_end <= ?")
            + " ORDER BY period_end DESC LIMIT ?";
        List<Object> args = new ArrayList<>(List.of(instrumentId, metric.name()));
        if (upToInclusiveOrNull != null) {
            args.add(Date.valueOf(upToInclusiveOrNull));
        }
        args.add(limit);

        List<FinancialEvidenceObservation> descending = jdbcTemplate.query(sql, ROW_MAPPER, args.toArray());
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
