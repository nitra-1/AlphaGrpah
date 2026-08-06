package com.alphagraph.corporate.commentary;

import com.alphagraph.corporate.api.CommitmentLevel;
import com.alphagraph.corporate.api.GuidanceDirection;
import com.alphagraph.corporate.api.ManagementObservation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads observations - the input to {@link ManagementCommentaryEngine}. Public since Module 2.9:
 * the AI Analyst needs individual guidance statements (e.g. "management raised FY27 revenue
 * guidance to 30%"), not just {@code ManagementSnapshotReader}'s aggregated score.
 * {@link #findRecentAcrossAllInstruments} was added in Module 2.10 (Decision Intelligence API
 * Layer) for the "Management Guidance Changes" dashboard widget.
 */
@Component
public class ManagementObservationReader {

    static final String CONSUMER = "MANAGEMENT_COMMENTARY_ENGINE";

    private static final RowMapper<ManagementObservation> ROW_MAPPER = (rs, rowNum) -> new ManagementObservation(
        (UUID) rs.getObject("id"), (UUID) rs.getObject("document_id"), (UUID) rs.getObject("instrument_id"),
        rs.getString("symbol"), rs.getString("metric_type"), rs.getString("guidance_value"),
        rs.getObject("guidance_value_numeric") == null ? null : rs.getDouble("guidance_value_numeric"),
        rs.getString("guidance_period"), GuidanceDirection.valueOf(rs.getString("direction")),
        rs.getString("signal"), CommitmentLevel.valueOf(rs.getString("commitment_level")),
        rs.getDouble("extraction_confidence"), rs.getTimestamp("observed_at").toInstant()
    );

    private static final String SELECT_COLUMNS = """
        id, document_id, instrument_id, symbol, metric_type, guidance_value, guidance_value_numeric,
        guidance_period, direction, signal, commitment_level, extraction_confidence, observed_at
        """;

    private final JdbcTemplate jdbcTemplate;

    ManagementObservationReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<UUID> findDistinctInstrumentIds() {
        return jdbcTemplate.query(
            "SELECT DISTINCT instrument_id FROM corporate.management_observations",
            (rs, rowNum) -> (UUID) rs.getObject("instrument_id")
        );
    }

    public List<ManagementObservation> findByInstrument(UUID instrumentId) {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM corporate.management_observations WHERE instrument_id = ? ORDER BY observed_at DESC",
            ROW_MAPPER, instrumentId
        );
    }

    /** Every guidance statement across all instruments in the last {@code lookbackDays}, newest first. */
    public List<ManagementObservation> findRecentAcrossAllInstruments(int lookbackDays) {
        Instant since = Instant.now().minusSeconds(lookbackDays * 86400L);
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM corporate.management_observations WHERE observed_at >= ? ORDER BY observed_at DESC",
            ROW_MAPPER, Timestamp.from(since)
        );
    }
}
