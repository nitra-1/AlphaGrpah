package com.alphagraph.learning.outcomes;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs 5 minutes after {@code learning.snapshot.DecisionSnapshotScheduler} (21:50 IST), so today's snapshot exists before this looks for newly-computable horizons across every snapshot on record. */
@Component
public class ForwardOutcomeScheduler {

    private static final String CRON_955PM_IST = "0 55 21 * * *";
    private static final String JOB_NAME = "forward-outcome-tracking";

    private final ForwardOutcomeOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public ForwardOutcomeScheduler(ForwardOutcomeOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_955PM_IST, zone = "Asia/Kolkata")
    public void runForwardOutcomeTracking() {
        tracker.run(JOB_NAME, orchestrator::computeAllPending, "learning.forward_outcomes", "computed_at");
    }
}
