package com.alphagraph.ownership.transformation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs at 19:18 IST, 8 minutes after {@code ownership-transformation} (19:10) - confirmed free
 * against the real, current {@code CronMonitoringRepository.JOB_SCHEDULES} map (between
 * {@code ownership-transformation} 19:10 and {@code market-inflection} 19:20). Only depends on
 * Ownership's own Stage 2, not the other domains' Stage 2 jobs.
 */
@Component
public class OwnershipTransformationSequenceScheduler {

    private static final String CRON_718PM_IST = "0 18 19 * * *";
    private static final String JOB_NAME = "ownership-transformation-sequences";
    private static final String BACKFILL_JOB_NAME = "ownership-transformation-sequences-backfill";

    private final OwnershipTransformationSequenceOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public OwnershipTransformationSequenceScheduler(OwnershipTransformationSequenceOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_718PM_IST, zone = "Asia/Kolkata")
    public void runOwnershipTransformationSequences() {
        tracker.run(JOB_NAME, orchestrator::run, "ownership.transformation_sequences", "computed_at");
    }

    /** One-off historical catch-up, manually triggered only (no {@code @Scheduled} - same convention every existing backfill job already uses). */
    public void runOwnershipTransformationSequencesBackfill() {
        tracker.run(BACKFILL_JOB_NAME, orchestrator::backfill, "ownership.transformation_sequences", "computed_at");
    }
}
