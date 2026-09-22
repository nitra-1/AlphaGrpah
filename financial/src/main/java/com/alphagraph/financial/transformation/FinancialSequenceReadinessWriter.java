package com.alphagraph.financial.transformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.UUID;

/** Upserts one {@code (instrument_id, as_of_date)} row - no reasons sub-table, this is a plain status row. */
@Component
class FinancialSequenceReadinessWriter {

    private final JdbcTemplate jdbcTemplate;

    FinancialSequenceReadinessWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(FinancialSequenceReadinessResult result) {
        jdbcTemplate.update(
            """
            INSERT INTO financial.transformation_sequence_readiness (id, instrument_id, symbol, as_of_date, history_periods, readiness)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                symbol = EXCLUDED.symbol, history_periods = EXCLUDED.history_periods, readiness = EXCLUDED.readiness
            """,
            UUID.randomUUID(), result.instrumentId(), result.symbol(), Date.valueOf(result.asOfDate()),
            result.historyPeriods(), result.readiness().name()
        );
    }
}
