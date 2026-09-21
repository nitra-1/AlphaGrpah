package com.alphagraph.market.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.UUID;

/** Upserts one {@code (instrument_id, as_of_date)} row - no reasons sub-table, this is a plain status row. */
@Component
class MarketSequenceReadinessWriter {

    private final JdbcTemplate jdbcTemplate;

    MarketSequenceReadinessWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(MarketSequenceReadinessResult result) {
        jdbcTemplate.update(
            """
            INSERT INTO market.transformation_sequence_readiness (id, instrument_id, symbol, as_of_date, history_sessions, readiness)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                symbol = EXCLUDED.symbol, history_sessions = EXCLUDED.history_sessions, readiness = EXCLUDED.readiness
            """,
            UUID.randomUUID(), result.instrumentId(), result.symbol(), Date.valueOf(result.asOfDate()),
            result.historySessions(), result.readiness().name()
        );
    }
}
