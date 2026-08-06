package com.alphagraph.decision.engine;

import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.decision.api.DecisionScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads decision.decision_scores back - {@link DecisionScoreStore} was write-only until Module
 * 3.2 (Watchlist), the first consumer that needs to show an instrument's current Rank alongside
 * something else (a watched instrument's latest signal), the same "nothing had to read this back
 * until X needed it as an input" pattern every other domain's first reader followed.
 */
@Component
public class DecisionScoreReader {

    private static final RowMapper<DecisionScore> ROW_MAPPER = (rs, rowNum) -> new DecisionScore(
        (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
        rs.getDouble("swing_score"), DecisionRating.valueOf(rs.getString("swing_rating")), (Integer) rs.getObject("swing_rank"),
        rs.getDouble("long_term_score"), DecisionRating.valueOf(rs.getString("long_term_rating")), (Integer) rs.getObject("long_term_rank"),
        rs.getObject("technical_score") == null ? null : rs.getDouble("technical_score"),
        rs.getObject("fundamental_score") == null ? null : rs.getDouble("fundamental_score"),
        rs.getObject("institutional_score") == null ? null : rs.getDouble("institutional_score"),
        rs.getObject("sector_score") == null ? null : rs.getDouble("sector_score"),
        rs.getObject("risk_score") == null ? null : rs.getDouble("risk_score"),
        rs.getObject("corporate_score") == null ? null : rs.getDouble("corporate_score"),
        rs.getDouble("confidence"), rs.getInt("rule_set_version"), rs.getTimestamp("computed_at").toInstant()
    );

    private static final String SELECT_COLUMNS = """
        instrument_id, symbol, as_of_date, swing_score, swing_rating, swing_rank,
        long_term_score, long_term_rating, long_term_rank, technical_score, fundamental_score,
        institutional_score, sector_score, risk_score, corporate_score, confidence, rule_set_version, computed_at
        """;

    private final JdbcTemplate jdbcTemplate;

    public DecisionScoreReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<DecisionScore> findLatest(UUID instrumentId) {
        List<DecisionScore> rows = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM decision.decision_scores WHERE instrument_id = ? ORDER BY as_of_date DESC LIMIT 1",
            ROW_MAPPER, instrumentId
        );
        return rows.stream().findFirst();
    }

    /** Every score on record for one instrument, newest first. */
    public List<DecisionScore> findHistory(UUID instrumentId) {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM decision.decision_scores WHERE instrument_id = ? ORDER BY as_of_date DESC",
            ROW_MAPPER, instrumentId
        );
    }

    /** Every instrument's latest score, highest Swing Score first. */
    public List<DecisionScore> findAllLatest() {
        List<DecisionScore> rows = jdbcTemplate.query(
            "SELECT DISTINCT ON (instrument_id) " + SELECT_COLUMNS +
            " FROM decision.decision_scores ORDER BY instrument_id, as_of_date DESC",
            ROW_MAPPER
        );
        return rows.stream().sorted((a, b) -> Double.compare(b.swingScore(), a.swingScore())).toList();
    }

    /** Every instrument scored on exactly this date - added in Module 3.6 (Daily AI Report), which needs to diff one day's whole cohort against the previous day's rather than walking each instrument's history individually. */
    public List<DecisionScore> findAllByDate(LocalDate date) {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM decision.decision_scores WHERE as_of_date = ?",
            ROW_MAPPER, Date.valueOf(date)
        );
    }
}
