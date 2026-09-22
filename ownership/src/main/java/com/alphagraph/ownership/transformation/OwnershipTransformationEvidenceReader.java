package com.alphagraph.ownership.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Reads {@code ownership.transformation_evidence} back for Stage 3 - Stage 1's own writer
 * ({@link TransformationEvidenceWriter}) was write-only until Stage 3 needed a real ascending
 * per-metric window, same read-back gap Market's own evidence reader closed for its Stage 3.
 */
@Component
class OwnershipTransformationEvidenceReader {

    private static final String SELECT_COLUMNS = """
        instrument_id, symbol, metric_name, period_end, prior_period_end, value, prior_value,
        change_pp, velocity_pp_per_quarter, persistence_quarters, confidence
        """;

    private final JdbcTemplate jdbcTemplate;

    OwnershipTransformationEvidenceReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The most recent real prefix ending at {@code upToInclusiveOrNull}, ascending. Always
     * {@code ORDER BY period_end DESC LIMIT ?} first, then reversed in Java - never fetch the
     * oldest {@code limit} rows and truncate. {@code upToInclusiveOrNull == null} means "latest"
     * (today's live run); a real historical date makes point-in-time backfill correct by
     * construction, since the query itself can never see a row after the bound.
     */
    List<OwnershipEvidenceObservation> findHistory(UUID instrumentId, TransformationMetric metric, LocalDate upToInclusiveOrNull, int limit) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM ownership.transformation_evidence WHERE instrument_id = ? AND metric_name = ?"
            + (upToInclusiveOrNull == null ? "" : " AND period_end <= ?")
            + " ORDER BY period_end DESC LIMIT ?";
        List<Object> args = new ArrayList<>(List.of(instrumentId, metric.name()));
        if (upToInclusiveOrNull != null) {
            args.add(Date.valueOf(upToInclusiveOrNull));
        }
        args.add(limit);

        List<OwnershipEvidenceObservation> descending = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new OwnershipEvidenceObservation(
                TransformationMetric.valueOf(rs.getString("metric_name")), (UUID) rs.getObject("instrument_id"), rs.getString("symbol"),
                rs.getDate("period_end").toLocalDate(), rs.getDate("prior_period_end") == null ? null : rs.getDate("prior_period_end").toLocalDate(),
                rs.getBigDecimal("value"), rs.getBigDecimal("prior_value"), rs.getBigDecimal("change_pp"), rs.getBigDecimal("velocity_pp_per_quarter"),
                rs.getInt("persistence_quarters"), rs.getDouble("confidence")
            ),
            args.toArray()
        );
        List<OwnershipEvidenceObservation> ascending = new ArrayList<>(descending);
        Collections.reverse(ascending);
        return ascending;
    }

    /** Every instrument with at least one real evidence row - Stage 3's own driving universe, same choice Market's Stage 3 made. */
    List<UUID> findAllInstrumentIds() {
        return jdbcTemplate.query(
            "SELECT DISTINCT instrument_id FROM ownership.transformation_evidence",
            (rs, rowNum) -> (UUID) rs.getObject("instrument_id")
        );
    }
}
