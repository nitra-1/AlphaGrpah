package com.alphagraph.corporate.transformation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs at 18:02 IST, right after {@code corporate-actions} (18:00) so the same day's freshly-ingested actions are already in place. */
@Component
public class CapitalAllocationScheduler {

    private static final String CRON_602PM_IST = "0 2 18 * * *";
    private static final String JOB_NAME = "capital-allocation-evidence";

    private final CapitalAllocationOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public CapitalAllocationScheduler(CapitalAllocationOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_602PM_IST, zone = "Asia/Kolkata")
    public void runCapitalAllocationEvidence() {
        tracker.run(JOB_NAME, orchestrator::run, "corporate.transformation_evidence", "computed_at");
    }
}
