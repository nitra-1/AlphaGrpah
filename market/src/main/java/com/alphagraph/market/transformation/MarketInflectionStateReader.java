package com.alphagraph.market.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads back {@code market.inflection_states} for the one self-referential state
 * ({@code EARLY_PRICE_PARTICIPATION}) that needs to know what the state *was* before today.
 *
 * <p>Strictly {@code as_of_date < ?}, never just "the latest row" - with the state table's unique
 * key on {@code (instrument_id, as_of_date)}, a same-day retry would otherwise read the row this
 * same run already wrote earlier today, making an idempotent retry behave like a new trading day
 * and risking a spurious {@code EARLY_PRICE_PARTICIPATION} fire on a mere re-run. The strict bound
 * makes a same-day retry naturally see nothing new - still whatever the last genuinely *earlier*
 * day's row was, if any.
 */
@Component
class MarketInflectionStateReader {

    private final JdbcTemplate jdbcTemplate;

    MarketInflectionStateReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The latest valid earlier state - "prior trading state", not literally "yesterday" (real weekends/holidays/failed jobs mean the last real row may be several calendar days back). */
    Optional<String> findPriorTradingState(UUID instrumentId, LocalDate asOfDate) {
        List<String> rows = jdbcTemplate.query(
            "SELECT primary_state FROM market.inflection_states WHERE instrument_id = ? AND as_of_date < ? ORDER BY as_of_date DESC LIMIT 1",
            (rs, rowNum) -> rs.getString("primary_state"),
            instrumentId, Date.valueOf(asOfDate)
        );
        return rows.stream().findFirst();
    }
}
