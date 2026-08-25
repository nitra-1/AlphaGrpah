package com.alphagraph.api.admin;

import java.time.Instant;

/**
 * One row in the unified admin cron-status view - either one of the 9 registered
 * common.etl.ScheduledPipeline beans (source="pipeline", backed by
 * scheduler.pipeline_definitions/pipeline_executions) or one of the 17 standalone
 * @Scheduled orchestrator calls (source="job", backed by scheduler.job_runs). lastStatus is
 * null and lastSummary is "Never run yet" for a job that hasn't fired since the app was last
 * deployed with tracking - real absence of data, not a fabricated placeholder.
 *
 * <p>{@code missedToday} is true when this cron's scheduled time has already passed today (IST)
 * but no run - of any status, including a still-{@code RUNNING} one - is on record for today; see
 * {@link CronMonitoringRepository#computeMissedToday}. The admin monitoring page uses this to show
 * a Retry button only where one is actually meaningful.
 */
public record CronStatusDto(
    String name,
    String schedule,
    String source,
    String lastStatus,
    Instant lastStartedAt,
    Instant lastFinishedAt,
    String lastSummary,
    boolean missedToday
) {
}
