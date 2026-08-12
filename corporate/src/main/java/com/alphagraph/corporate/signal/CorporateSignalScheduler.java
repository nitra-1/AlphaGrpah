package com.alphagraph.corporate.signal;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 15 minutes after {@code NewsCatalystScheduler} (Module 2.6, 19:30 IST) - by then all four
 * corporate engines this module combines have had their own chance to run for the day.
 */
@Component
public class CorporateSignalScheduler {

    private static final String CRON_745PM_IST = "0 45 19 * * *";
    private static final String JOB_NAME = "corporate-signal";

    private final CorporateSignalOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public CorporateSignalScheduler(CorporateSignalOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_745PM_IST, zone = "Asia/Kolkata")
    public void runCorporateSignalUpdate() {
        tracker.run(JOB_NAME, orchestrator::recomputeAllInstruments, "corporate.corporate_scores", "computed_at");
    }
}
