package com.alphagraph.corporate.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.UUID;

/** Upserts one {@code (instrument_id, as_of_date)} row - no reasons sub-table, this is a plain status row. */
@Component
class CapitalAllocationSequenceReadinessWriter {

    private final JdbcTemplate jdbcTemplate;

    CapitalAllocationSequenceReadinessWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(CapitalAllocationSequenceReadinessResult result) {
        jdbcTemplate.update(
            """
            INSERT INTO corporate.transformation_sequence_readiness (id, instrument_id, symbol, as_of_date, history_days, readiness)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                symbol = EXCLUDED.symbol, history_days = EXCLUDED.history_days, readiness = EXCLUDED.readiness
            """,
            UUID.randomUUID(), result.instrumentId(), result.symbol(), Date.valueOf(result.asOfDate()),
            result.historyDays(), result.readiness().name()
        );
    }
}
