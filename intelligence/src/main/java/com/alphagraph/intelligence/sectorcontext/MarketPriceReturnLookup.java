package com.alphagraph.intelligence.sectorcontext;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Reads {@code market.transformation_evidence}'s {@code PRICE_RETURN_20D} rows back directly by
 * raw SQL, cross-schema by value only - reuses Market Tier 1's already-computed 20-day return
 * rather than recomputing it, per the approved plan. Same {@code market.pricing.DiscoveryCandidateLookup}
 * precedent (a raw-SQL-by-value read needing no new Java dependency), just from {@code intelligence}
 * rather than another domain module.
 */
@Component
class MarketPriceReturnLookup {

    private static final String METRIC_NAME = "PRICE_RETURN_20D";

    private final JdbcTemplate jdbcTemplate;

    MarketPriceReturnLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The most recent {@code limit} PRICE_RETURN_20D points for one instrument, ascending by trade date. */
    List<PriceReturnPoint> findRecentAscending(UUID instrumentId, int limit) {
        List<PriceReturnPoint> descending = jdbcTemplate.query(
            """
            SELECT trade_date, value FROM market.transformation_evidence
            WHERE instrument_id = ? AND metric_name = ?
            ORDER BY trade_date DESC LIMIT ?
            """,
            (rs, rowNum) -> new PriceReturnPoint(rs.getDate("trade_date").toLocalDate(), rs.getBigDecimal("value")),
            instrumentId, METRIC_NAME, limit
        );
        List<PriceReturnPoint> ascending = new ArrayList<>(descending);
        Collections.reverse(ascending);
        return ascending;
    }
}
