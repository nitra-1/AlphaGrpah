package com.alphagraph.sector.transformation;

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
 * Reads {@code sector.transformation_evidence} back - {@link SectorContextEvidenceWriter} was
 * write-only until Stage 2 needed to read it. Stage 2 never recomputes change/persistence from raw
 * prices/scores - {@code intelligence.sectorcontext.SectorContextEngine} already computed and
 * persisted them, so Stage 2 just reads the single most recent row per (instrument, metric) and
 * interprets it.
 */
@Component
class SectorContextEvidenceReader {

    private static final String SELECT_COLUMNS = """
        instrument_id, symbol, metric_name, as_of_date, prior_as_of_date, value, prior_value,
        change, persistence_days, confidence
        """;

    private final JdbcTemplate jdbcTemplate;

    SectorContextEvidenceReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<SectorEvidenceObservation> findLatest(UUID instrumentId, SectorMetric metric) {
        List<SectorEvidenceObservation> rows = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM sector.transformation_evidence " +
            "WHERE instrument_id = ? AND metric_name = ? ORDER BY as_of_date DESC LIMIT 1",
            (rs, rowNum) -> new SectorEvidenceObservation(
                SectorMetric.valueOf(rs.getString("metric_name")), (UUID) rs.getObject("instrument_id"), rs.getString("symbol"),
                rs.getDate("as_of_date").toLocalDate(), rs.getDate("prior_as_of_date") == null ? null : rs.getDate("prior_as_of_date").toLocalDate(),
                rs.getBigDecimal("value"), rs.getBigDecimal("prior_value"), rs.getBigDecimal("change"),
                rs.getInt("persistence_days"), rs.getDouble("confidence")
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
    List<SectorEvidenceObservation> findHistory(UUID instrumentId, SectorMetric metric, LocalDate upToInclusiveOrNull, int limit) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM sector.transformation_evidence WHERE instrument_id = ? AND metric_name = ?"
            + (upToInclusiveOrNull == null ? "" : " AND as_of_date <= ?")
            + " ORDER BY as_of_date DESC LIMIT ?";
        List<Object> args = new ArrayList<>(List.of(instrumentId, metric.name()));
        if (upToInclusiveOrNull != null) {
            args.add(Date.valueOf(upToInclusiveOrNull));
        }
        args.add(limit);

        List<SectorEvidenceObservation> descending = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new SectorEvidenceObservation(
                SectorMetric.valueOf(rs.getString("metric_name")), (UUID) rs.getObject("instrument_id"), rs.getString("symbol"),
                rs.getDate("as_of_date").toLocalDate(), rs.getDate("prior_as_of_date") == null ? null : rs.getDate("prior_as_of_date").toLocalDate(),
                rs.getBigDecimal("value"), rs.getBigDecimal("prior_value"), rs.getBigDecimal("change"),
                rs.getInt("persistence_days"), rs.getDouble("confidence")
            ),
            args.toArray()
        );
        List<SectorEvidenceObservation> ascending = new ArrayList<>(descending);
        Collections.reverse(ascending);
        return ascending;
    }

    /** Real total observations ever recorded for this (instrument, metric) - not a consecutive-streak count, used to scale confidence by real history depth (see SectorInflectionEngine.depthTierBase). */
    int countObservations(UUID instrumentId, SectorMetric metric) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sector.transformation_evidence WHERE instrument_id = ? AND metric_name = ?",
            Integer.class, instrumentId, metric.name()
        );
        return count == null ? 0 : count;
    }

    /** Every instrument with at least one real evidence row - Stage 2's own driving universe, not reference.instruments' raw universe. */
    List<UUID> findAllInstrumentIds() {
        return jdbcTemplate.query(
            "SELECT DISTINCT instrument_id FROM sector.transformation_evidence",
            (rs, rowNum) -> (UUID) rs.getObject("instrument_id")
        );
    }
}
