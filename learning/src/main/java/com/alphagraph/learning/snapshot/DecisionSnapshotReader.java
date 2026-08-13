package com.alphagraph.learning.snapshot;

import com.alphagraph.decision.api.DecisionRating;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class DecisionSnapshotReader {

    private static final RowMapper<DecisionSnapshot> ROW_MAPPER = (rs, rowNum) -> new DecisionSnapshot(
        (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
        rs.getDouble("swing_score"), DecisionRating.valueOf(rs.getString("swing_rating")), (Integer) rs.getObject("swing_rank"),
        rs.getDouble("long_term_score"), DecisionRating.valueOf(rs.getString("long_term_rating")), (Integer) rs.getObject("long_term_rank"),
        rs.getObject("technical_score") == null ? null : rs.getDouble("technical_score"),
        rs.getObject("fundamental_score") == null ? null : rs.getDouble("fundamental_score"),
        rs.getObject("institutional_score") == null ? null : rs.getDouble("institutional_score"),
        rs.getObject("sector_score") == null ? null : rs.getDouble("sector_score"),
        rs.getObject("risk_score") == null ? null : rs.getDouble("risk_score"),
        rs.getObject("corporate_score") == null ? null : rs.getDouble("corporate_score"),
        rs.getDouble("confidence"), rs.getInt("rule_set_version"), rs.getTimestamp("decision_computed_at").toInstant(),
        toLocalDate(rs, "technical_score_as_of_date"), (Integer) rs.getObject("technical_rule_set_version"), toInstant(rs, "technical_computed_at"),
        toLocalDate(rs, "fundamental_score_as_of_date"), (Integer) rs.getObject("fundamental_rule_set_version"), toInstant(rs, "fundamental_computed_at"),
        toLocalDate(rs, "institutional_score_as_of_date"), (Integer) rs.getObject("institutional_rule_set_version"), toInstant(rs, "institutional_computed_at"),
        toLocalDate(rs, "sector_score_as_of_date"), (Integer) rs.getObject("sector_rule_set_version"), toInstant(rs, "sector_computed_at"),
        toLocalDate(rs, "risk_score_as_of_date"), (Integer) rs.getObject("risk_rule_set_version"), toInstant(rs, "risk_computed_at"),
        toLocalDate(rs, "corporate_score_as_of_date"), (Integer) rs.getObject("corporate_rule_set_version"), toInstant(rs, "corporate_computed_at"),
        (UUID) rs.getObject("decision_run_id"), (Integer) rs.getObject("swing_rank_universe_size"), (Integer) rs.getObject("long_term_rank_universe_size"),
        rs.getTimestamp("captured_at").toInstant()
    );

    private static LocalDate toLocalDate(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    private static java.time.Instant toInstant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static final String SELECT_COLUMNS = """
        instrument_id, symbol, as_of_date, swing_score, swing_rating, swing_rank,
        long_term_score, long_term_rating, long_term_rank, technical_score, fundamental_score,
        institutional_score, sector_score, risk_score, corporate_score, confidence, rule_set_version,
        decision_computed_at,
        technical_score_as_of_date, technical_rule_set_version, technical_computed_at,
        fundamental_score_as_of_date, fundamental_rule_set_version, fundamental_computed_at,
        institutional_score_as_of_date, institutional_rule_set_version, institutional_computed_at,
        sector_score_as_of_date, sector_rule_set_version, sector_computed_at,
        risk_score_as_of_date, risk_rule_set_version, risk_computed_at,
        corporate_score_as_of_date, corporate_rule_set_version, corporate_computed_at,
        decision_run_id, swing_rank_universe_size, long_term_rank_universe_size,
        captured_at
        """;

    private final JdbcTemplate jdbcTemplate;

    public DecisionSnapshotReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Every snapshot older than today - "today" has zero elapsed trading days, so it can never have a computable outcome yet and isn't worth fetching price history for. */
    public List<DecisionSnapshot> findAllBefore(LocalDate today) {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM learning.decision_snapshots WHERE as_of_date < ? ORDER BY as_of_date ASC",
            ROW_MAPPER, java.sql.Date.valueOf(today)
        );
    }

    /** The one immutable snapshot for an (instrument, day) - used by ForwardOutcomeOrchestrator to re-fetch the original decision when recomputing an invalidated outcome. */
    public java.util.Optional<DecisionSnapshot> findOne(UUID instrumentId, LocalDate asOfDate) {
        List<DecisionSnapshot> rows = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM learning.decision_snapshots WHERE instrument_id = ? AND as_of_date = ?",
            ROW_MAPPER, instrumentId, java.sql.Date.valueOf(asOfDate)
        );
        return rows.stream().findFirst();
    }
}
