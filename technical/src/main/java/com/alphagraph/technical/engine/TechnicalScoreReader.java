package com.alphagraph.technical.engine;

import com.alphagraph.technical.api.BreakoutStatus;
import com.alphagraph.technical.api.Momentum;
import com.alphagraph.technical.api.Trend;
import com.alphagraph.technical.api.TechnicalScore;
import com.alphagraph.technical.api.VolumeState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads technical.technical_scores directly - it's this module's own table. Added in Module 1.9
 * (Risk Engine): Module 1.5 only needed a {@link TechnicalScoreWriter}, since nothing had to read
 * a technical score back until the Risk Engine needed it as an input.
 */
@Component
public class TechnicalScoreReader {

    private final JdbcTemplate jdbcTemplate;

    public TechnicalScoreReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TechnicalScore> findLatest(UUID instrumentId) {
        List<TechnicalScore> rows = jdbcTemplate.query(
            """
            SELECT ts.instrument_id, i.symbol, ts.as_of_date, ts.trend, ts.trend_confidence,
                   ts.momentum, ts.volume_state, ts.breakout_status, ts.stage, ts.market_behaviour_score,
                   ts.sma20, ts.sma50, ts.sma200, ts.weekly_sma30, ts.rsi14, ts.macd_line, ts.macd_signal,
                   ts.macd_histogram, ts.adx14, ts.atr14, ts.obv, ts.relative_volume, ts.rule_set_version,
                   ts.computed_at
            FROM technical.technical_scores ts
            JOIN reference.instruments i ON i.id = ts.instrument_id
            WHERE ts.instrument_id = ?
            ORDER BY ts.as_of_date DESC
            LIMIT 1
            """,
            this::mapRow,
            instrumentId
        );
        return rows.stream().findFirst();
    }

    private TechnicalScore mapRow(ResultSet rs, int rowNum) throws SQLException {
        Long obv = (Long) rs.getObject("obv");
        return new TechnicalScore(
            (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
            Trend.valueOf(rs.getString("trend")), rs.getDouble("trend_confidence"),
            Momentum.valueOf(rs.getString("momentum")), VolumeState.valueOf(rs.getString("volume_state")),
            BreakoutStatus.valueOf(rs.getString("breakout_status")), (Integer) rs.getObject("stage"),
            rs.getDouble("market_behaviour_score"),
            toDouble(rs.getBigDecimal("sma20")), toDouble(rs.getBigDecimal("sma50")), toDouble(rs.getBigDecimal("sma200")),
            toDouble(rs.getBigDecimal("weekly_sma30")), toDouble(rs.getBigDecimal("rsi14")), toDouble(rs.getBigDecimal("macd_line")),
            toDouble(rs.getBigDecimal("macd_signal")), toDouble(rs.getBigDecimal("macd_histogram")),
            toDouble(rs.getBigDecimal("adx14")), toDouble(rs.getBigDecimal("atr14")), obv,
            toDouble(rs.getBigDecimal("relative_volume")), rs.getInt("rule_set_version"),
            rs.getTimestamp("computed_at").toInstant()
        );
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
