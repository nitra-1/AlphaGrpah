package com.alphagraph.corporate.commentary;

import com.alphagraph.corporate.api.ManagementObservation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/** Persists one {@link ManagementObservation} (Layer 1) as a {@code corporate.management_observations} row. */
@Component
class ManagementObservationWriter {

    private final JdbcTemplate jdbcTemplate;

    ManagementObservationWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(ManagementObservation observation) {
        jdbcTemplate.update(
            """
            INSERT INTO corporate.management_observations (
                id, document_id, instrument_id, symbol, metric_type, guidance_value, guidance_value_numeric,
                guidance_period, direction, signal, commitment_level, extraction_confidence, observed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            observation.id(), observation.documentId(), observation.instrumentId(), observation.symbol(),
            observation.metricType(), observation.guidanceValue(), observation.guidanceValueNumeric(),
            observation.guidancePeriod(), observation.direction().name(), observation.signal(),
            observation.commitmentLevel().name(), observation.extractionConfidence(), Timestamp.from(observation.observedAt())
        );
    }
}
