package com.alphagraph.market.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.UUID;

/**
 * Appends one row to {@code market.transformation_evidence} - never updated in place.
 * {@code ON CONFLICT (instrument_id, metric_name, trade_date) DO NOTHING} makes this safe to
 * rerun the same day without ever duplicating or overwriting an already-evidenced trading day,
 * same convention as {@code ownership.transformation.TransformationEvidenceWriter}.
 */
@Component
class MarketTransformationEvidenceWriter {

    private static final String SOURCE = "MARKET_DAILY_PRICES";
    private static final int RULE_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;

    MarketTransformationEvidenceWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(MarketEvidenceObservation observation) {
        jdbcTemplate.update(
            """
            INSERT INTO market.transformation_evidence (
                id, instrument_id, symbol, metric_name, trade_date, prior_trade_date, value, prior_value,
                change, velocity_per_day, persistence_days, confidence, source, rule_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, metric_name, trade_date) DO NOTHING
            """,
            UUID.randomUUID(), observation.instrumentId(), observation.symbol(), observation.metric().name(),
            Date.valueOf(observation.tradeDate()), observation.priorTradeDate() == null ? null : Date.valueOf(observation.priorTradeDate()),
            observation.value(), observation.priorValue(), observation.change(), observation.velocityPerDay(),
            observation.persistenceDays(), observation.confidence(), SOURCE, RULE_VERSION
        );
    }
}
