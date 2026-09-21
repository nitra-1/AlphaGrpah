package com.alphagraph.intelligence.riskcontradiction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Raw SQL against {@code financial.inflection_states}/{@code _state_reasons} and
 * {@code financial.transformation_evidence}, cross-schema by value only - see this package's own
 * javadoc. {@link #findStateAsOf} mirrors {@code risk.engine.RiskScoreReader.findAsOf}'s own
 * already-existing precedent in this exact codebase - used for
 * {@code CAPITAL_RAISE_WITH_WEAK_BUSINESS_INFLECTION}'s cross-domain anchor read, never a financial
 * reading from after the anchor event.
 */
@Component
class FinancialGrowthReader {

    private final JdbcTemplate jdbcTemplate;

    FinancialGrowthReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<FamilyStateSignal> findLatestState(UUID instrumentId) {
        return findState("SELECT id, as_of_date, confidence FROM financial.inflection_states WHERE instrument_id = ? ORDER BY as_of_date DESC LIMIT 1", instrumentId);
    }

    /** Latest state with as_of_date on or before {@code anchor} - never a financial reading from after the anchor event. */
    Optional<FamilyStateSignal> findStateAsOf(UUID instrumentId, LocalDate anchor) {
        return findState(
            "SELECT id, as_of_date, confidence FROM financial.inflection_states WHERE instrument_id = ? AND as_of_date <= ? ORDER BY as_of_date DESC LIMIT 1",
            instrumentId, Date.valueOf(anchor)
        );
    }

    private Optional<FamilyStateSignal> findState(String sql, Object... args) {
        List<StateRow> states = jdbcTemplate.query(
            sql, (rs, rowNum) -> new StateRow((UUID) rs.getObject("id"), rs.getDate("as_of_date").toLocalDate(), rs.getDouble("confidence")), args
        );
        if (states.isEmpty()) {
            return Optional.empty();
        }
        StateRow state = states.get(0);
        List<ReasonCode> reasons = jdbcTemplate.query(
            "SELECT reason_code, metric_value, evidence_reference FROM financial.inflection_state_reasons WHERE state_id = ?",
            (rs, rowNum) -> ReasonCode.fromRow(rs.getString("reason_code"), rs.getBigDecimal("metric_value"), rs.getString("evidence_reference")),
            state.id()
        );
        return Optional.of(new FamilyStateSignal(state.asOfDate(), state.confidence(), reasons));
    }

    Optional<RawMetricPoint> findLatestOperatingMarginChange(UUID instrumentId) {
        List<RawMetricPoint> rows = jdbcTemplate.query(
            "SELECT period_end, value, change, confidence FROM financial.transformation_evidence " +
            "WHERE instrument_id = ? AND metric_name = 'OPERATING_MARGIN' ORDER BY period_end DESC LIMIT 1",
            (rs, rowNum) -> new RawMetricPoint(
                rs.getDate("period_end").toLocalDate(), rs.getBigDecimal("value"), rs.getBigDecimal("change"), rs.getDouble("confidence")
            ),
            instrumentId
        );
        return rows.stream().findFirst();
    }

    private record StateRow(UUID id, LocalDate asOfDate, double confidence) {
    }
}
