package com.alphagraph.ownership.transformation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs at 19:10 IST, safely after both {@code ownership-shareholding-pattern} (18:00) and {@code xbrl-shareholding-enrichment} (18:05) have had time to complete for the day. */
@Component
public class OwnershipTransformationScheduler {

    private static final String CRON_710PM_IST = "0 10 19 * * *";
    private static final String JOB_NAME = "ownership-transformation";
    private static final String BACKFILL_JOB_NAME = "ownership-transformation-backfill";

    private final OwnershipTransformationOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public OwnershipTransformationScheduler(OwnershipTransformationOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_710PM_IST, zone = "Asia/Kolkata")
    public void runOwnershipTransformation() {
        tracker.run(JOB_NAME, orchestrator::run, "ownership.transformation_states", "computed_at");
    }

    /** One-off historical catch-up, manually triggered only (no {@code @Scheduled} - never fires on its own, see claude.md). */
    public void runOwnershipTransformationBackfill() {
        tracker.run(BACKFILL_JOB_NAME, orchestrator::backfill, "ownership.transformation_evidence", "computed_at");
    }
}
