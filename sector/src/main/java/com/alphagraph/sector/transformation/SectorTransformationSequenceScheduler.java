package com.alphagraph.sector.transformation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs at 19:33 IST, 7 minutes after {@code sector-inflection} (19:26) - confirmed free against the
 * real, current {@code CronMonitoringRepository.JOB_SCHEDULES} map (nothing occupied between
 * {@code news-catalyst} 19:30 and {@code corporate-signal} 20:00).
 */
@Component
public class SectorTransformationSequenceScheduler {

    private static final String CRON_733PM_IST = "0 33 19 * * *";
    private static final String JOB_NAME = "sector-transformation-sequences";
    private static final String BACKFILL_JOB_NAME = "sector-transformation-sequences-backfill";

    private final SectorTransformationSequenceOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public SectorTransformationSequenceScheduler(SectorTransformationSequenceOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_733PM_IST, zone = "Asia/Kolkata")
    public void runSectorTransformationSequences() {
        tracker.run(JOB_NAME, orchestrator::run, "sector.transformation_sequences", "computed_at");
    }

    /** One-off historical catch-up, manually triggered only (no {@code @Scheduled} - same convention every existing backfill job already uses). */
    public void runSectorTransformationSequencesBackfill() {
        tracker.run(BACKFILL_JOB_NAME, orchestrator::backfill, "sector.transformation_sequences", "computed_at");
    }
}
