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
        rs.getDouble("confidence"), rs.getInt("rule_set_version"),
        rs.getTimestamp("decision_computed_at").toInstant(), rs.getTimestamp("captured_at").toInstant()
    );

    private static final String SELECT_COLUMNS = """
        instrument_id, symbol, as_of_date, swing_score, swing_rating, swing_rank,
        long_term_score, long_term_rating, long_term_rank, technical_score, fundamental_score,
        institutional_score, sector_score, risk_score, corporate_score, confidence, rule_set_version,
        decision_computed_at, captured_at
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
}
