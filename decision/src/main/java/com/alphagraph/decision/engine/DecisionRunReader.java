package com.alphagraph.decision.engine;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public: {@code learning.snapshot.DecisionSnapshotOrchestrator} reads this cross-module to decide whether a day's decision_scores cohort is safe to archive. */
@Component
public class DecisionRunReader {

    private static final RowMapper<DecisionRun> ROW_MAPPER = (rs, rowNum) -> new DecisionRun(
        (UUID) rs.getObject("id"), rs.getDate("as_of_date").toLocalDate(),
        rs.getTimestamp("started_at").toInstant(),
        rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant(),
        rs.getString("status"),
        (Integer) rs.getObject("instrument_count"), (Integer) rs.getObject("ranked_count"),
        rs.getInt("rule_set_version")
    );

    private static final String SELECT_COLUMNS =
        "id, as_of_date, started_at, completed_at, status, instrument_count, ranked_count, rule_set_version";

    private final JdbcTemplate jdbcTemplate;

    public DecisionRunReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The run for a given date, regardless of status - callers decide what to do with a RUNNING/FAILED run themselves. */
    public Optional<DecisionRun> findByDate(LocalDate asOfDate) {
        List<DecisionRun> rows = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM decision.decision_runs WHERE as_of_date = ?",
            ROW_MAPPER, Date.valueOf(asOfDate)
        );
        return rows.stream().findFirst();
    }
}
