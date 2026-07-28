package com.alphagraph.technical.engine;

import com.alphagraph.technical.api.TechnicalScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Upserts on (instrument_id, as_of_date) — idempotent, matching every other module's
 * Loader/ScoreWriter convention (docs/002_Engine_Architecture.md §5).
 */
@Component
public class TechnicalScoreWriter {

    private final JdbcTemplate jdbcTemplate;

    public TechnicalScoreWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void write(TechnicalScore score) {
        jdbcTemplate.update(
            """
            INSERT INTO technical.technical_scores
                (id, instrument_id, as_of_date, trend, trend_confidence, momentum, volume_state,
                 breakout_status, stage, market_behaviour_score, sma20, sma50, sma200, weekly_sma30,
                 rsi14, macd_line, macd_signal, macd_histogram, adx14, atr14, obv, relative_volume,
                 rule_set_version, computed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                trend = EXCLUDED.trend, trend_confidence = EXCLUDED.trend_confidence,
                momentum = EXCLUDED.momentum, volume_state = EXCLUDED.volume_state,
                breakout_status = EXCLUDED.breakout_status, stage = EXCLUDED.stage,
                market_behaviour_score = EXCLUDED.market_behaviour_score,
                sma20 = EXCLUDED.sma20, sma50 = EXCLUDED.sma50, sma200 = EXCLUDED.sma200,
                weekly_sma30 = EXCLUDED.weekly_sma30, rsi14 = EXCLUDED.rsi14,
                macd_line = EXCLUDED.macd_line, macd_signal = EXCLUDED.macd_signal,
                macd_histogram = EXCLUDED.macd_histogram, adx14 = EXCLUDED.adx14, atr14 = EXCLUDED.atr14,
                obv = EXCLUDED.obv, relative_volume = EXCLUDED.relative_volume,
                rule_set_version = EXCLUDED.rule_set_version, computed_at = EXCLUDED.computed_at
            """,
            UUID.randomUUID(), score.instrumentId(), score.asOfDate(), score.trend().name(),
            score.trendConfidence(), score.momentum().name(), score.volumeState().name(),
            score.breakoutStatus().name(), score.stage(), score.marketBehaviourScore(),
            score.sma20(), score.sma50(), score.sma200(), score.weeklySma30(),
            score.rsi14(), score.macdLine(), score.macdSignal(), score.macdHistogram(),
            score.adx14(), score.atr14(), score.obv(), score.relativeVolume(),
            score.ruleSetVersion(), Timestamp.from(score.computedAt())
        );
    }
}
