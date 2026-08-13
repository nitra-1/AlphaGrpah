package com.alphagraph.decision.engine;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lifecycle for one day's {@link DecisionRun}: {@link #startOrResume} upserts on {@code as_of_date}
 * (a manual re-run the same day resumes the same row rather than creating an orphaned run_id that
 * decision_scores' own per-day upsert would leave stale), then {@link #markCompleted} or
 * {@link #markFailed} closes it out.
 */
@Component
class DecisionRunStore {

    private final JdbcTemplate jdbcTemplate;

    DecisionRunStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    UUID startOrResume(LocalDate asOfDate, int ruleSetVersion) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO decision.decision_runs (id, as_of_date, started_at, completed_at, status, instrument_count, ranked_count, rule_set_version)
            VALUES (?, ?, now(), NULL, 'RUNNING', NULL, NULL, ?)
            ON CONFLICT (as_of_date) DO UPDATE SET
                started_at = now(), completed_at = NULL, status = 'RUNNING',
                instrument_count = NULL, ranked_count = NULL, rule_set_version = EXCLUDED.rule_set_version
            RETURNING id
            """,
            UUID.class, UUID.randomUUID(), Date.valueOf(asOfDate), ruleSetVersion
        );
    }

    void markCompleted(UUID runId, int instrumentCount, int rankedCount) {
        jdbcTemplate.update(
            "UPDATE decision.decision_runs SET status = 'COMPLETED', completed_at = now(), instrument_count = ?, ranked_count = ? WHERE id = ?",
            instrumentCount, rankedCount, runId
        );
    }

    void markFailed(UUID runId) {
        jdbcTemplate.update(
            "UPDATE decision.decision_runs SET status = 'FAILED', completed_at = now() WHERE id = ?",
            runId
        );
    }
}
