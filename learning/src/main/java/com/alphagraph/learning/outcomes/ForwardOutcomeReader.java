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
}
