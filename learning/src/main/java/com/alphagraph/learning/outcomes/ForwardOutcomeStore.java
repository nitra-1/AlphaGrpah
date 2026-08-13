package com.alphagraph.learning.outcomes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

@Component
class ForwardOutcomeStore {

    private static final String INSERT_SQL = """
        INSERT INTO learning.forward_outcomes (
            id, instrument_id, symbol, as_of_date, horizon_days, outcome_date,
            reference_price, outcome_price, forward_return_percentage,
            swing_rating, swing_directionally_correct, long_term_rating, long_term_directionally_correct,
            technical_signal_correct, fundamental_signal_correct, institutional_signal_correct,
            sector_signal_correct, risk_signal_correct, corporate_signal_correct,
            status, price_adjustment_watermark,
            market_benchmark_instrument_id, market_benchmark_return_percentage, market_benchmark_outcome_date, excess_return_market_percentage,
            sector_benchmark_instrument_id, sector_benchmark_return_percentage, sector_benchmark_outcome_date, excess_return_sector_percentage,
            mfe_percentage, mae_percentage
        ) VALUES (
            ?, ?, ?, ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?
        )
        ON CONFLICT (instrument_id, as_of_date, horizon_days) DO NOTHING
        """;

    private static final String RECOMPUTE_SQL = """
        UPDATE learning.forward_outcomes SET
            reference_price = ?, outcome_price = ?, forward_return_percentage = ?,
            swing_rating = ?, swing_directionally_correct = ?, long_term_rating = ?, long_term_directionally_correct = ?,
            technical_signal_correct = ?, fundamental_signal_correct = ?, institutional_signal_correct = ?,
            sector_signal_correct = ?, risk_signal_correct = ?, corporate_signal_correct = ?,
            status = ?, price_adjustment_watermark = ?,
            market_benchmark_instrument_id = ?, market_benchmark_return_percentage = ?, market_benchmark_outcome_date = ?, excess_return_market_percentage = ?,
            sector_benchmark_instrument_id = ?, sector_benchmark_return_percentage = ?, sector_benchmark_outcome_date = ?, excess_return_sector_percentage = ?,
            mfe_percentage = ?, mae_percentage = ?,
            recomputed_at = now()
        WHERE instrument_id = ? AND as_of_date = ? AND horizon_days = ? AND status = 'INVALIDATED'
        """;

    private final JdbcTemplate jdbcTemplate;

    ForwardOutcomeStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** A genuinely new (instrument, as_of_date, horizon) row - never overwrites an existing one, even a stale CURRENT one; that's what {@link #recompute} is for. */
    void save(ForwardOutcome outcome) {
        jdbcTemplate.update(INSERT_SQL, new Object[] {
            UUID.randomUUID(), outcome.instrumentId(), outcome.symbol(), Date.valueOf(outcome.asOfDate()),
            outcome.horizonDays(), Date.valueOf(outcome.outcomeDate()),
            outcome.referencePrice(), outcome.outcomePrice(), outcome.forwardReturnPercentage(),
            outcome.swingRating().name(), outcome.swingDirectionallyCorrect(),
            outcome.longTermRating().name(), outcome.longTermDirectionallyCorrect(),
            outcome.technicalSignalCorrect(), outcome.fundamentalSignalCorrect(), outcome.institutionalSignalCorrect(),
            outcome.sectorSignalCorrect(), outcome.riskSignalCorrect(), outcome.corporateSignalCorrect(),
            outcome.status(), toTimestamp(outcome.priceAdjustmentWatermark()),
            outcome.marketBenchmarkInstrumentId(), outcome.marketBenchmarkReturnPercentage(),
            toDate(outcome.marketBenchmarkOutcomeDate()), outcome.excessReturnMarketPercentage(),
            outcome.sectorBenchmarkInstrumentId(), outcome.sectorBenchmarkReturnPercentage(),
            toDate(outcome.sectorBenchmarkOutcomeDate()), outcome.excessReturnSectorPercentage(),
            outcome.mfePercentage(), outcome.maePercentage()
        });
    }

    /**
     * Overwrites a row this system itself flagged {@code INVALIDATED} - guarded by that status in
     * the WHERE clause so this never blindly overwrites a {@code CURRENT} row (only
     * {@link ForwardOutcomeInvalidator} sets a row to {@code INVALIDATED} in the first place).
     * Whole-row: every field the engine recomputes gets rewritten together, so a row is never left
     * with e.g. a corrected return but a stale MFE/MAE from before the correction.
     */
    void recompute(ForwardOutcome outcome) {
        jdbcTemplate.update(RECOMPUTE_SQL, new Object[] {
            outcome.referencePrice(), outcome.outcomePrice(), outcome.forwardReturnPercentage(),
            outcome.swingRating().name(), outcome.swingDirectionallyCorrect(),
            outcome.longTermRating().name(), outcome.longTermDirectionallyCorrect(),
            outcome.technicalSignalCorrect(), outcome.fundamentalSignalCorrect(), outcome.institutionalSignalCorrect(),
            outcome.sectorSignalCorrect(), outcome.riskSignalCorrect(), outcome.corporateSignalCorrect(),
            outcome.status(), toTimestamp(outcome.priceAdjustmentWatermark()),
            outcome.marketBenchmarkInstrumentId(), outcome.marketBenchmarkReturnPercentage(),
            toDate(outcome.marketBenchmarkOutcomeDate()), outcome.excessReturnMarketPercentage(),
            outcome.sectorBenchmarkInstrumentId(), outcome.sectorBenchmarkReturnPercentage(),
            toDate(outcome.sectorBenchmarkOutcomeDate()), outcome.excessReturnSectorPercentage(),
            outcome.mfePercentage(), outcome.maePercentage(),
            outcome.instrumentId(), Date.valueOf(outcome.asOfDate()), outcome.horizonDays()
        });
    }

    /** Flips exactly one row CURRENT -> INVALIDATED, by primary key - guarded so it never touches a row already INVALIDATED/RECOMPUTING (idempotent if the invalidator's own scan overlaps a previous run). */
    void markInvalidated(UUID instrumentId, java.time.LocalDate asOfDate, int horizonDays) {
        jdbcTemplate.update(
            "UPDATE learning.forward_outcomes SET status = 'INVALIDATED' WHERE instrument_id = ? AND as_of_date = ? AND horizon_days = ? AND status = 'CURRENT'",
            instrumentId, Date.valueOf(asOfDate), horizonDays
        );
    }

    private static Date toDate(java.time.LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private static Timestamp toTimestamp(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
