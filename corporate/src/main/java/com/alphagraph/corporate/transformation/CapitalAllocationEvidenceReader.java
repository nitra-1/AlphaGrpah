package com.alphagraph.corporate.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads {@code corporate.transformation_evidence} back - {@link CapitalAllocationEvidenceWriter}
 * was write-only until Stage 2 needed to read it. Stage 2 never recomputes the rolling count from
 * raw actions - {@link CapitalAllocationEngine} already computed and persisted it, so Stage 2 just
 * reads the single most recent row per (instrument, metric) and interprets it.
 */
@Component
class CapitalAllocationEvidenceReader {

    private static final String SELECT_COLUMNS = """
        instrument_id, symbol, metric_name, as_of_date, prior_as_of_date, value, prior_value,
        change, entered_event_count, exited_event_count, velocity_per_day, persistence_days, confidence, window_days
        """;

    private final JdbcTemplate jdbcTemplate;

    CapitalAllocationEvidenceReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<CapitalAllocationEvidenceObservation> findLatest(UUID instrumentId, CapitalAllocationMetric metric) {
        List<CapitalAllocationEvidenceObservation> rows = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM corporate.transformation_evidence " +
            "WHERE instrument_id = ? AND metric_name = ? ORDER BY as_of_date DESC LIMIT 1",
            (rs, rowNum) -> new CapitalAllocationEvidenceObservation(
                CapitalAllocationMetric.valueOf(rs.getString("metric_name")), (UUID) rs.getObject("instrument_id"), rs.getString("symbol"),
                rs.getDate("as_of_date").toLocalDate(), rs.getDate("prior_as_of_date").toLocalDate(),
                rs.getInt("value"), rs.getInt("prior_value"), rs.getInt("change"),
                rs.getInt("entered_event_count"), rs.getInt("exited_event_count"), rs.getInt("velocity_per_day"),
                rs.getInt("persistence_days"), rs.getDouble("confidence"), rs.getInt("window_days")
            ),
            instrumentId, metric.name()
        );
        return rows.stream().findFirst();
    }

    /**
     * Added for Stage 3 sequence detection (docs/008 §16's point-in-time rule): the most recent
     * real prefix ending at {@code upToInclusiveOrNull}, ascending. Always
     * {@code ORDER BY as_of_date DESC LIMIT ?} first, then reversed in Java - never fetch the
     * oldest {@code limit} rows and truncate. {@code upToInclusiveOrNull == null} means "latest".
     */
    List<CapitalAllocationEvidenceObservation> findHistory(UUID instrumentId, CapitalAllocationMetric metric, LocalDate upToInclusiveOrNull, int limit) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM corporate.transformation_evidence WHERE instrument_id = ? AND metric_name = ?"
            + (upToInclusiveOrNull == null ? "" : " AND as_of_date <= ?")
            + " ORDER BY as_of_date DESC LIMIT ?";
        List<Object> args = new ArrayList<>(List.of(instrumentId, metric.name()));
        if (upToInclusiveOrNull != null) {
            args.add(Date.valueOf(upToInclusiveOrNull));
        }
        args.add(limit);

        List<CapitalAllocationEvidenceObservation> descending = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new CapitalAllocationEvidenceObservation(
                CapitalAllocationMetric.valueOf(rs.getString("metric_name")), (UUID) rs.getObject("instrument_id"), rs.getString("symbol"),
                rs.getDate("as_of_date").toLocalDate(), rs.getDate("prior_as_of_date").toLocalDate(),
                rs.getInt("value"), rs.getInt("prior_value"), rs.getInt("change"),
                rs.getInt("entered_event_count"), rs.getInt("exited_event_count"), rs.getInt("velocity_per_day"),
                rs.getInt("persistence_days"), rs.getDouble("confidence"), rs.getInt("window_days")
            ),
            args.toArray()
        );
        List<CapitalAllocationEvidenceObservation> ascending = new ArrayList<>(descending);
        Collections.reverse(ascending);
        return ascending;
    }

    /** Every instrument with at least one real evidence row (either metric) - Stage 1's own real universe, not reference.instruments' raw universe. */
    List<UUID> findAllInstrumentIds() {
        return jdbcTemplate.query(
            "SELECT DISTINCT instrument_id FROM corporate.transformation_evidence",
            (rs, rowNum) -> (UUID) rs.getObject("instrument_id")
        );
    }
}
