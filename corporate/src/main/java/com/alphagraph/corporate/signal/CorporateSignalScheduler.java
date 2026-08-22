package com.alphagraph.corporate.signal;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 30 minutes after {@code NewsCatalystScheduler} (Module 2.6, 19:30 IST) - by then all four
 * corporate engines this module combines have had their own chance to run for the day. Moved from
 * 19:45 to 20:00 on 2026-08-22 - a 15-minute buffer was already safely more than enough given both
 * jobs consistently finish in well under a second (confirmed via real {@code scheduler.job_runs}
 * history), but 19:45 itself was never a candidate: this app has no custom {@code TaskScheduler}
 * bean, so {@code @Scheduled} jobs run on Spring Boot's default single-threaded scheduler, and two
 * jobs due at the exact same cron second have no documented, reliable ordering guarantee - moving
 * this to exactly 19:30 (coincident with {@code NewsCatalystScheduler}) risked this job silently
 * reading yesterday's {@code news_catalyst_scores} row on any day it happened to run first, with no
 * error or warning. 20:00 keeps a full, unambiguous buffer instead of closing it to zero, and still
 * leaves {@code decision-scoring} (21:45 IST, reads {@code corporate.corporate_scores}) a
 * comfortable 1h45m margin.
 */
@Component
public class CorporateSignalScheduler {

    private static final String CRON_8PM_IST = "0 0 20 * * *";
    private static final String JOB_NAME = "corporate-signal";

    private final CorporateSignalOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public CorporateSignalScheduler(CorporateSignalOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_8PM_IST, zone = "Asia/Kolkata")
    public void runCorporateSignalUpdate() {
        tracker.run(JOB_NAME, orchestrator::recomputeAllInstruments, "corporate.corporate_scores", "computed_at");
    }
}
