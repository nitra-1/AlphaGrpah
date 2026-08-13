package com.alphagraph.learning.outcomes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
class ForwardOutcomeReader {

    private final JdbcTemplate jdbcTemplate;

    ForwardOutcomeReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Horizons already computed for one (instrument, decision date) - lets the orchestrator only compute what's missing, not re-derive everything on every run. */
    Set<Integer> findComputedHorizons(UUID instrumentId, LocalDate asOfDate) {
        List<Integer> horizons = jdbcTemplate.queryForList(
            "SELECT horizon_days FROM learning.forward_outcomes WHERE instrument_id = ? AND as_of_date = ?",
            Integer.class, instrumentId, Date.valueOf(asOfDate)
        );
        return new HashSet<>(horizons);
    }

    /** Every row ForwardOutcomeInvalidator has flagged since the last recompute pass - identifying triple only, since recomputing needs the original immutable DecisionSnapshot, not this row's own (now-stale) values. */
    List<InvalidatedOutcome> findInvalidated() {
        return jdbcTemplate.query(
            "SELECT instrument_id, as_of_date, horizon_days FROM learning.forward_outcomes WHERE status = 'INVALIDATED'",
            (rs, rowNum) -> new InvalidatedOutcome(
                (UUID) rs.getObject("instrument_id"), rs.getDate("as_of_date").toLocalDate(), rs.getInt("horizon_days")
            )
        );
    }

    /** Every row still CURRENT - what ForwardOutcomeInvalidator checks each corporate action against. */
    List<CurrentOutcome> findCurrent() {
        return jdbcTemplate.query(
            """
            SELECT instrument_id, as_of_date, horizon_days, outcome_date, price_adjustment_watermark
            FROM learning.forward_outcomes WHERE status = 'CURRENT'
            """,
            (rs, rowNum) -> new CurrentOutcome(
                (UUID) rs.getObject("instrument_id"), rs.getDate("as_of_date").toLocalDate(), rs.getInt("horizon_days"),
                rs.getDate("outcome_date").toLocalDate(),
                rs.getTimestamp("price_adjustment_watermark") == null ? null : rs.getTimestamp("price_adjustment_watermark").toInstant()
            )
        );
    }

    record InvalidatedOutcome(UUID instrumentId, LocalDate asOfDate, int horizonDays) {
    }

    record CurrentOutcome(UUID instrumentId, LocalDate asOfDate, int horizonDays, LocalDate outcomeDate, java.time.Instant priceAdjustmentWatermark) {
    }
}
