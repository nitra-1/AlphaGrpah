package com.alphagraph.corporate.commentary;

import com.alphagraph.corporate.api.CommitmentLevel;
import com.alphagraph.corporate.api.GuidanceDirection;
import com.alphagraph.corporate.api.ManagementObservation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Reads every observation recorded for one instrument - the input to {@link ManagementCommentaryEngine}. */
@Component
class ManagementObservationReader {

    static final String CONSUMER = "MANAGEMENT_COMMENTARY_ENGINE";

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

    List<ManagementObservation> findByInstrument(UUID instrumentId) {
        return jdbcTemplate.query(
            """
            SELECT id, document_id, instrument_id, symbol, metric_type, guidance_value, guidance_value_numeric,
                   guidance_period, direction, signal, commitment_level, extraction_confidence, observed_at
            FROM corporate.management_observations WHERE instrument_id = ? ORDER BY observed_at DESC
            """,
            (rs, rowNum) -> new ManagementObservation(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("document_id"), (UUID) rs.getObject("instrument_id"),
                rs.getString("symbol"), rs.getString("metric_type"), rs.getString("guidance_value"),
                rs.getObject("guidance_value_numeric") == null ? null : rs.getDouble("guidance_value_numeric"),
                rs.getString("guidance_period"), GuidanceDirection.valueOf(rs.getString("direction")),
                rs.getString("signal"), CommitmentLevel.valueOf(rs.getString("commitment_level")),
                rs.getDouble("extraction_confidence"), rs.getTimestamp("observed_at").toInstant()
            ),
            instrumentId
        );
    }
}
