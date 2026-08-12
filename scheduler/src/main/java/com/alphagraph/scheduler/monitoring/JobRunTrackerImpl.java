package com.alphagraph.scheduler.monitoring;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Writes to {@code scheduler.job_runs}, alongside this module's existing
 * {@code scheduler.pipeline_executions} - for the ~15 standalone {@code @Scheduled} orchestrator
 * callers across the domain engines that have no {@code common.etl.ScheduledPipeline} tracking of
 * their own. Lives here (not in {@code common}, which is deliberately framework-free) for the same
 * reason {@code PipelineOrchestrator} does - this module already owns cross-cutting execution
 * tracking and already has Spring/JdbcTemplate.
 *
 * A failure here is swallowed, not rethrown: an uncaught exception from a {@code @Scheduled}
 * method already fails silently today (Spring just logs it, the run leaves no record at all) -
 * catching it here doesn't change that failure mode, it only makes the failure visible in
 * {@code scheduler.job_runs} instead of invisible.
 */
@Component
public class JobRunTrackerImpl implements JobRunTracker {

    private static final Logger log = LoggerFactory.getLogger(JobRunTrackerImpl.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-z_]+\\.[a-z_]+$");
    private static final Pattern SAFE_COLUMN = Pattern.compile("^[a-z_]+$");
    private static final int MAX_ERROR_LENGTH = 2000;

    private final JdbcTemplate jdbcTemplate;

    public JobRunTrackerImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String jobName, Runnable work) {
        run(jobName, work, null, null);
    }

    @Override
    public void run(String jobName, Runnable work, String summaryTable, String summaryTimestampColumn) {
        UUID id = UUID.randomUUID();
        Instant startedAt = Instant.now();
        jdbcTemplate.update(
            "INSERT INTO scheduler.job_runs (id, job_name, status, started_at) VALUES (?, ?, 'RUNNING', ?)",
            id, jobName, Timestamp.from(startedAt)
        );

        try {
            work.run();
            Instant finishedAt = Instant.now();
            String summary = summaryTable == null ? null : summarize(summaryTable, summaryTimestampColumn, startedAt, finishedAt);
            jdbcTemplate.update(
                "UPDATE scheduler.job_runs SET status = 'SUCCESS', finished_at = ?, summary = ? WHERE id = ?",
                Timestamp.from(finishedAt), summary, id
            );
        } catch (Exception e) {
            log.error("Scheduled job '{}' failed", jobName, e);
            jdbcTemplate.update(
                "UPDATE scheduler.job_runs SET status = 'FAILED', finished_at = ?, error_message = ? WHERE id = ?",
                Timestamp.from(Instant.now()), truncate(e.getMessage()), id
            );
        }
    }

    private String summarize(String table, String column, Instant startedAt, Instant finishedAt) {
        if (!SAFE_IDENTIFIER.matcher(table).matches() || !SAFE_COLUMN.matcher(column).matches()) {
            throw new IllegalArgumentException("Refusing non-identifier-looking table/column: " + table + "/" + column);
        }
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + table + " WHERE " + column + " BETWEEN ? AND ?",
            Long.class, Timestamp.from(startedAt), Timestamp.from(finishedAt)
        );
        return count + " row(s) written to " + table;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
