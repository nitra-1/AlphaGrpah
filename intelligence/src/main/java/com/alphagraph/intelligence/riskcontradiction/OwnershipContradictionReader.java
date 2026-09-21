package com.alphagraph.intelligence.riskcontradiction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Raw SQL against {@code ownership.transformation_states}/{@code _state_reasons}, cross-schema by
 * value only - see this package's own javadoc for why (no Java dependency on
 * {@code ownership.transformation}'s package-private classes, and {@code intelligence} already has
 * an approved Gradle dependency on {@code ownership}).
 */
@Component
class OwnershipContradictionReader {

    private final JdbcTemplate jdbcTemplate;

    OwnershipContradictionReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<FamilyStateSignal> findLatestState(UUID instrumentId) {
        List<StateRow> states = jdbcTemplate.query(
            "SELECT id, as_of_date, confidence FROM ownership.transformation_states WHERE instrument_id = ? ORDER BY as_of_date DESC LIMIT 1",
            (rs, rowNum) -> new StateRow((UUID) rs.getObject("id"), rs.getDate("as_of_date").toLocalDate(), rs.getDouble("confidence")),
            instrumentId
        );
        if (states.isEmpty()) {
            return Optional.empty();
        }
        StateRow state = states.get(0);
        List<ReasonCode> reasons = jdbcTemplate.query(
            "SELECT reason_code, metric_value, evidence_reference FROM ownership.transformation_state_reasons WHERE state_id = ?",
            (rs, rowNum) -> ReasonCode.fromRow(rs.getString("reason_code"), rs.getBigDecimal("metric_value"), rs.getString("evidence_reference")),
            state.id()
        );
        return Optional.of(new FamilyStateSignal(state.asOfDate(), state.confidence(), reasons));
    }

    private record StateRow(UUID id, LocalDate asOfDate, double confidence) {
    }
}
