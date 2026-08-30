package com.alphagraph.ownership.interpretation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Raw SQL over {@code market.discovered_prices} - cross-schema by value, the same established
 * pattern {@code ownership.deals.MarketLiquidityReader} already uses, just returning full OHLCV +
 * delivery % rows rather than only the 20-session turnover average. Both "sessions after" and
 * "sessions before" are trading-session (row-count), not calendar-day, windowed.
 */
@Component
class DiscoveredPriceHistoryReader {

    private final JdbcTemplate jdbcTemplate;

    DiscoveredPriceHistoryReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Ascending by trade_date, strictly after {@code date}, capped at {@code limit} - used for confirmation_sessions_elapsed and the T+1/T+3/T+5 post-anchor evidence. */
    List<DiscoveredPriceRow> findSessionsAfter(String symbol, LocalDate date, int limit) {
        return jdbcTemplate.query(
            """
            SELECT trade_date, open_price, high_price, low_price, close_price, volume, daily_traded_value, delivery_percentage
            FROM market.discovered_prices
            WHERE symbol = ? AND trade_date > ?
            ORDER BY trade_date ASC
            LIMIT ?
            """,
            DiscoveredPriceHistoryReader::mapRow, symbol, Date.valueOf(date), limit
        );
    }

    /** Ascending by trade_date, strictly before {@code date}, the {@code limit} most recent such sessions - used for the pre-event baseline (delivery %, volume). */
    List<DiscoveredPriceRow> findSessionsBefore(String symbol, LocalDate date, int limit) {
        List<DiscoveredPriceRow> descending = jdbcTemplate.query(
            """
            SELECT trade_date, open_price, high_price, low_price, close_price, volume, daily_traded_value, delivery_percentage
            FROM market.discovered_prices
            WHERE symbol = ? AND trade_date < ?
            ORDER BY trade_date DESC
            LIMIT ?
            """,
            DiscoveredPriceHistoryReader::mapRow, symbol, Date.valueOf(date), limit
        );
        return descending.reversed();
    }

    private static DiscoveredPriceRow mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DiscoveredPriceRow(
            rs.getDate("trade_date").toLocalDate(), rs.getBigDecimal("open_price"), rs.getBigDecimal("high_price"),
            rs.getBigDecimal("low_price"), rs.getBigDecimal("close_price"), rs.getLong("volume"),
            rs.getBigDecimal("daily_traded_value"), rs.getBigDecimal("delivery_percentage")
        );
    }
}
