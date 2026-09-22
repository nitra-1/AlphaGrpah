package com.alphagraph.sector.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.UUID;

/**
 * Upserts one {@code (instrument_id, as_of_date, sequence_type)} row - an instrument may
 * legitimately have multiple simultaneous sequences, so unlike every Stage 2 writer this key
 * includes {@code sequence_type} too. Reasons deleted and reinserted every time, same convention
 * every Stage 2 writer already uses.
 */
@Component
class SectorTransformationSequenceWriter {

    private static final int RULE_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;

    SectorTransformationSequenceWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(SectorSequenceResult result) {
        UUID sequenceId = jdbcTemplate.query(
            """
            INSERT INTO sector.transformation_sequences (
                id, instrument_id, symbol, as_of_date, sequence_type, sequence_phase, current_step,
                total_steps, first_step_date, last_step_date, sequence_strength, confidence, rule_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date, sequence_type) DO UPDATE SET
                symbol = EXCLUDED.symbol, sequence_phase = EXCLUDED.sequence_phase, current_step = EXCLUDED.current_step,
                total_steps = EXCLUDED.total_steps, first_step_date = EXCLUDED.first_step_date, last_step_date = EXCLUDED.last_step_date,
                sequence_strength = EXCLUDED.sequence_strength, confidence = EXCLUDED.confidence, rule_version = EXCLUDED.rule_version
            RETURNING id
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            UUID.randomUUID(), result.instrumentId(), result.symbol(), Date.valueOf(result.asOfDate()),
            result.sequenceType().name(), result.sequencePhase().name(), result.currentStep(), result.totalSteps(),
            result.firstStepDate() == null ? null : Date.valueOf(result.firstStepDate()),
            result.lastStepDate() == null ? null : Date.valueOf(result.lastStepDate()),
            result.sequenceStrength(), result.confidence(), RULE_VERSION
        ).get(0);

        jdbcTemplate.update("DELETE FROM sector.transformation_sequence_reasons WHERE sequence_id = ?", sequenceId);
        for (ReasonCode reason : result.reasons()) {
            jdbcTemplate.update(
                "INSERT INTO sector.transformation_sequence_reasons (id, sequence_id, reason_code, metric_value, evidence_reference) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), sequenceId, reason.code(), reason.metricValue(), reason.evidenceReference()
            );
        }
    }
}
