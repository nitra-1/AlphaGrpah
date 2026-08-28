package com.alphagraph.ownership.deals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Genuine 20-trading-session ADTV (average daily traded value) for a Discovery candidate symbol,
 * as of a given deal's date - the primary significance denominator for
 * {@link DealMaterialityEngine}'s Deal Value / ADTV ratio. Reads {@code market.discovered_prices}
 * directly by raw SQL, cross-schema by value only, the same established pattern
 * {@code ownership.pattern.OwnershipInstrumentLookup} already uses for
 * {@code reference.instruments} - {@code ownership} gains no Java dependency on the
 * {@code market} module.
 *
 * <p>Row-count (trading-session) windowed, not calendar-day windowed - the last 20 real rows
 * strictly before {@code dealDate}, matching {@code technical.indicators.RelativeVolume}'s
 * established "slice the last N array positions from an ascending-by-trade_date list" convention,
 * genuinely session-aware since a weekend/holiday simply has no row to select. {@code
 * market.discovered_prices.daily_traded_value} is nullable (a malformed/blank NSE turnover field
 * doesn't block capturing the rest of a row's OHLCV) - a row with a null turnover still counts as
 * a real trading session for other purposes, but can't contribute to a genuine turnover average,
 * so it's excluded here and the 20-row requirement re-checked after excluding it. Returns
 * {@link Optional#empty()} rather than a guess whenever fewer than 20 qualifying rows exist - a
 * symbol that IPO'd or was first discovered too recently correctly has no ADTV yet.
 */
@Component
class MarketLiquidityReader {

    private static final int TRADING_SESSIONS = 20;

    private final JdbcTemplate jdbcTemplate;

    MarketLiquidityReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<BigDecimal> findAdtv20(String symbol, LocalDate dealDate) {
        List<BigDecimal> turnovers = jdbcTemplate.query(
            """
            SELECT daily_traded_value FROM market.discovered_prices
            WHERE symbol = ? AND trade_date < ? AND daily_traded_value IS NOT NULL
            ORDER BY trade_date DESC
            LIMIT ?
            """,
            (rs, rowNum) -> rs.getBigDecimal("daily_traded_value"), symbol, Date.valueOf(dealDate), TRADING_SESSIONS
        );

        if (turnovers.size() < TRADING_SESSIONS) {
            return Optional.empty();
        }

        BigDecimal sum = turnovers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(sum.divide(BigDecimal.valueOf(TRADING_SESSIONS), 2, RoundingMode.HALF_UP));
    }
}
