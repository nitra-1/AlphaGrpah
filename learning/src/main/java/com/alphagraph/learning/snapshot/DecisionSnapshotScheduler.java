package com.alphagraph.learning.snapshot;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Runs 5 minutes after {@code decision.engine.DecisionScoringScheduler} (21:45 IST), so the day's whole decision_scores cohort is already written before it gets archived. */
@Component
public class DecisionSnapshotScheduler {

    private static final String CRON_950PM_IST = "0 50 21 * * *";
    private static final String JOB_NAME = "decision-snapshot-archive";

    private final DecisionSnapshotOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public DecisionSnapshotScheduler(DecisionSnapshotOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_950PM_IST, zone = "Asia/Kolkata")
    public void runDecisionSnapshotArchive() {
        tracker.run(JOB_NAME, () -> orchestrator.archiveForDate(LocalDate.now()), "learning.decision_snapshots", "captured_at");
    }
}
