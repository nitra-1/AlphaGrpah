package com.alphagraph.corporate.transformation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs at 19:31 IST, 7 minutes after {@code capital-allocation-inflection} (19:24) - confirmed free
 * against the real, current {@code CronMonitoringRepository.JOB_SCHEDULES} map (19:30 is taken by
 * {@code news-catalyst}; 19:31/19:32 are the only free minutes before {@code sector-transformation-
 * sequences} at 19:33), matching the same +6-to-8-minute convention every other domain's own Stage
 * 3 sequence job already uses relative to its own Stage 2 job.
 */
@Component
public class CapitalAllocationTransformationSequenceScheduler {

    private static final String CRON_731PM_IST = "0 31 19 * * *";
    private static final String JOB_NAME = "capital-allocation-transformation-sequences";
    private static final String BACKFILL_JOB_NAME = "capital-allocation-transformation-sequences-backfill";

    private final CapitalAllocationTransformationSequenceOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public CapitalAllocationTransformationSequenceScheduler(CapitalAllocationTransformationSequenceOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_731PM_IST, zone = "Asia/Kolkata")
    public void runCapitalAllocationTransformationSequences() {
        tracker.run(JOB_NAME, orchestrator::run, "corporate.transformation_sequences", "computed_at");
    }

    /** One-off historical catch-up, manually triggered only (no {@code @Scheduled} - same convention every existing backfill job already uses). */
    public void runCapitalAllocationTransformationSequencesBackfill() {
        tracker.run(BACKFILL_JOB_NAME, orchestrator::backfill, "corporate.transformation_sequences", "computed_at");
    }
}
