package com.alphagraph.api.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges two independent execution-tracking stores into one admin-facing cron status list:
 * scheduler.pipeline_definitions/pipeline_executions for the 9 registered
 * common.etl.ScheduledPipeline beans (pre-existing, module 0.10), and scheduler.job_runs for the
 * 17 standalone @Scheduled orchestrator calls that had zero tracking before this feature. The job
 * cron expressions are hardcoded here rather than read from a table because they're compiled
 * constants in each scheduler class (e.g. corporate.commentary.ManagementCommentaryScheduler's
 * CRON_915PM_IST) - copied by hand from those files, not guessed. If one of those constants
 * changes, this map needs a matching edit; there's no way around that without inventing a whole
 * second definitions table for 17 rows that never change at runtime.
 */
@Repository
public class CronMonitoringRepository {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final Map<String, String> JOB_SCHEDULES = new LinkedHashMap<>();

    static {
        JOB_SCHEDULES.put("document-processing", "0 15 18 * * *");
        JOB_SCHEDULES.put("knowledge-extraction", "0 30 18 * * *");
        JOB_SCHEDULES.put("technical-analysis", "0 30 18 * * *");
        JOB_SCHEDULES.put("financial-results-bridge", "0 35 18 * * *");
        JOB_SCHEDULES.put("fundamental-analysis", "0 40 18 * * *");
        JOB_SCHEDULES.put("corporate-event-extraction", "0 45 18 * * *");
        JOB_SCHEDULES.put("institutional-analysis", "0 50 18 * * *");
        JOB_SCHEDULES.put("sector-analysis", "0 55 18 * * *");
        JOB_SCHEDULES.put("risk-analysis", "0 0 19 * * *");
        JOB_SCHEDULES.put("order-book", "0 0 19 * * *");
        JOB_SCHEDULES.put("management-commentary", "0 15 19 * * *");
        JOB_SCHEDULES.put("news-catalyst", "0 30 19 * * *");
        JOB_SCHEDULES.put("corporate-signal", "0 0 20 * * *");
        JOB_SCHEDULES.put("decision-scoring", "0 45 21 * * *");
        JOB_SCHEDULES.put("decision-snapshot-archive", "0 50 21 * * *");
        JOB_SCHEDULES.put("forward-outcome-tracking", "0 55 21 * * *");
        JOB_SCHEDULES.put("daily-ai-report", "0 0 22 * * *");
    }

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock = Clock.system(IST);

    public CronMonitoringRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CronStatusDto> findAll() {
        List<CronStatusDto> merged = new ArrayList<>(findPipelineStatuses());
        merged.addAll(findJobStatuses());
        // schedule is always zero-padded "HH:mm IST daily" and every cron in this system fires
        // between 00:00-23:59 with no midnight wraparound, so a plain string sort on it is
        // already chronological - name is just the tiebreaker for crons sharing the same slot.
        merged.sort(Comparator.comparing(CronStatusDto::schedule).thenComparing(CronStatusDto::name));
        return merged;
    }

    private List<CronStatusDto> findPipelineStatuses() {
        return jdbcTemplate.query(
            """
            SELECT d.name, d.cron_expression,
                   e.status, e.started_at, e.finished_at, e.rows_read, e.rows_accepted, e.rows_rejected
            FROM scheduler.pipeline_definitions d
            LEFT JOIN LATERAL (
                SELECT * FROM scheduler.pipeline_executions pe
                WHERE pe.pipeline_id = d.id ORDER BY pe.started_at DESC LIMIT 1
            ) e ON true
            ORDER BY d.name
            """,
            (rs, rowNum) -> {
                String status = rs.getString("status");
                String summary = status == null ? "Never run yet" : "%d read, %d accepted, %d rejected".formatted(
                    rs.getInt("rows_read"), rs.getInt("rows_accepted"), rs.getInt("rows_rejected")
                );
                String cronExpression = rs.getString("cron_expression");
                Instant startedAt = toInstant(rs.getTimestamp("started_at"));
                return new CronStatusDto(
                    rs.getString("name"), humanizeCron(cronExpression), "pipeline",
                    status, startedAt, toInstant(rs.getTimestamp("finished_at")), summary,
                    computeMissedToday(cronExpression, startedAt, clock.instant())
                );
            }
        );
    }

    private List<CronStatusDto> findJobStatuses() {
        List<CronStatusDto> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : JOB_SCHEDULES.entrySet()) {
            String jobName = entry.getKey();
            List<CronStatusDto> rows = jdbcTemplate.query(
                """
                SELECT status, started_at, finished_at, summary, error_message
                FROM scheduler.job_runs WHERE job_name = ? ORDER BY started_at DESC LIMIT 1
                """,
                (rs, rowNum) -> {
                    String status = rs.getString("status");
                    String detail = "FAILED".equals(status) ? rs.getString("error_message") : rs.getString("summary");
                    Instant startedAt = toInstant(rs.getTimestamp("started_at"));
                    return new CronStatusDto(
                        jobName, humanizeCron(entry.getValue()), "job",
                        status, startedAt, toInstant(rs.getTimestamp("finished_at")), detail,
                        computeMissedToday(entry.getValue(), startedAt, clock.instant())
                    );
                },
                jobName
            );
            result.add(rows.isEmpty()
                ? new CronStatusDto(
                    jobName, humanizeCron(entry.getValue()), "job", null, null, null, "Never run yet",
                    computeMissedToday(entry.getValue(), null, clock.instant())
                )
                : rows.get(0));
        }
        return result;
    }

    private static String humanizeCron(String cronExpression) {
        LocalTime scheduledTime = parseScheduledTime(cronExpression);
        return scheduledTime == null ? cronExpression : "%02d:%02d IST daily".formatted(scheduledTime.getHour(), scheduledTime.getMinute());
    }

    /**
     * Every cron in this system is a simple daily "second minute hour * * *" expression (confirmed
     * across all 9 pipeline definitions and all 17 job constants - no cron in this codebase uses a
     * day-of-week/month restriction or fires more than once a day), so only fields[1]/[2] matter.
     * Returns null for anything that doesn't fit that shape rather than guessing.
     */
    private static LocalTime parseScheduledTime(String cronExpression) {
        if (cronExpression == null) {
            return null;
        }
        String[] parts = cronExpression.trim().split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        try {
            return LocalTime.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    /**
     * True when this cron's scheduled time has already passed today (IST) but no run - success,
     * failure, or still-{@code RUNNING} - is on record for today. A same-day run of any status
     * means there's nothing meaningful to retry (a currently-running job would just get a second
     * concurrent execution), so this is a same-day-or-not check, not a status check. {@code now} is
     * passed explicitly so this stays a pure, deterministically testable function.
     */
    static boolean computeMissedToday(String cronExpression, Instant lastStartedAt, Instant now) {
        LocalTime scheduledTime = parseScheduledTime(cronExpression);
        if (scheduledTime == null) {
            return false;
        }
        ZonedDateTime nowIst = now.atZone(IST);
        LocalDate today = nowIst.toLocalDate();
        if (nowIst.isBefore(today.atTime(scheduledTime).atZone(IST))) {
            return false;
        }
        return lastStartedAt == null || lastStartedAt.atZone(IST).toLocalDate().isBefore(today);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
