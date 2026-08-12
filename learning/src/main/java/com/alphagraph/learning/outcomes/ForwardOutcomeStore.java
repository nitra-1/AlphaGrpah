package com.alphagraph.learning.outcomes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.UUID;

@Component
class ForwardOutcomeStore {

    private final JdbcTemplate jdbcTemplate;

    ForwardOutcomeStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void save(ForwardOutcome outcome) {
        jdbcTemplate.update(
            """
            INSERT INTO learning.forward_outcomes (
                id, instrument_id, symbol, as_of_date, horizon_days, outcome_date,
                reference_price, outcome_price, forward_return_percentage,
                swing_rating, swing_directionally_correct, long_term_rating, long_term_directionally_correct,
                technical_signal_correct, fundamental_signal_correct, institutional_signal_correct,
                sector_signal_correct, risk_signal_correct, corporate_signal_correct
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date, horizon_days) DO NOTHING
            """,
            UUID.randomUUID(), outcome.instrumentId(), outcome.symbol(), Date.valueOf(outcome.asOfDate()),
            outcome.horizonDays(), Date.valueOf(outcome.outcomeDate()),
            outcome.referencePrice(), outcome.outcomePrice(), outcome.forwardReturnPercentage(),
            outcome.swingRating().name(), outcome.swingDirectionallyCorrect(),
            outcome.longTermRating().name(), outcome.longTermDirectionallyCorrect(),
            outcome.technicalSignalCorrect(), outcome.fundamentalSignalCorrect(), outcome.institutionalSignalCorrect(),
            outcome.sectorSignalCorrect(), outcome.riskSignalCorrect(), outcome.corporateSignalCorrect()
        );
    }
}
