package com.alphagraph.intelligence.riskcontradiction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Raw SQL against {@code market.inflection_states}/{@code _state_reasons} and
 * {@code market.transformation_evidence}, cross-schema by value only - see this package's own
 * javadoc.
 */
@Component
class MarketDeliveryReader {

    private final JdbcTemplate jdbcTemplate;

    MarketDeliveryReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<FamilyStateSignal> findLatestState(UUID instrumentId) {
        List<StateRow> states = jdbcTemplate.query(
            "SELECT id, as_of_date, confidence FROM market.inflection_states WHERE instrument_id = ? ORDER BY as_of_date DESC LIMIT 1",
            (rs, rowNum) -> new StateRow((UUID) rs.getObject("id"), rs.getDate("as_of_date").toLocalDate(), rs.getDouble("confidence")),
            instrumentId
        );
        if (states.isEmpty()) {
            return Optional.empty();
        }
        StateRow state = states.get(0);
        List<ReasonCode> reasons = jdbcTemplate.query(
            "SELECT reason_code, metric_value, evidence_reference FROM market.inflection_state_reasons WHERE state_id = ?",
            (rs, rowNum) -> ReasonCode.fromRow(rs.getString("reason_code"), rs.getBigDecimal("metric_value"), rs.getString("evidence_reference")),
            state.id()
        );
        return Optional.of(new FamilyStateSignal(state.asOfDate(), state.confidence(), reasons));
    }

    Optional<RawMetricPoint> findLatestPriceReturnChange(UUID instrumentId) {
        List<RawMetricPoint> rows = jdbcTemplate.query(
            "SELECT trade_date, value, change, confidence FROM market.transformation_evidence " +
            "WHERE instrument_id = ? AND metric_name = 'PRICE_RETURN_20D' ORDER BY trade_date DESC LIMIT 1",
            (rs, rowNum) -> new RawMetricPoint(
                rs.getDate("trade_date").toLocalDate(), rs.getBigDecimal("value"), rs.getBigDecimal("change"), rs.getDouble("confidence")
            ),
            instrumentId
        );
        return rows.stream().findFirst();
    }

    private record StateRow(UUID id, LocalDate asOfDate, double confidence) {
    }
}
