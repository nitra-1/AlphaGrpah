package com.alphagraph.market.transformation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs at 19:27 IST, 7 minutes after {@code market-inflection} (19:20) - confirmed free against
 * the real, current {@code CronMonitoringRepository.JOB_SCHEDULES} map (between
 * {@code sector-inflection} 19:26 and {@code risk-contradiction-inflection} 19:29). Only depends
 * on Market's own Stage 2, not the other domains' Stage 2 jobs.
 */
@Component
public class MarketTransformationSequenceScheduler {

    private static final String CRON_727PM_IST = "0 27 19 * * *";
    private static final String JOB_NAME = "market-transformation-sequences";
    private static final String BACKFILL_JOB_NAME = "market-transformation-sequences-backfill";

    private final MarketTransformationSequenceOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public MarketTransformationSequenceScheduler(MarketTransformationSequenceOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_727PM_IST, zone = "Asia/Kolkata")
    public void runMarketTransformationSequences() {
        tracker.run(JOB_NAME, orchestrator::run, "market.transformation_sequences", "computed_at");
    }

    /** One-off historical catch-up, manually triggered only (no {@code @Scheduled} - never fires on its own, same convention the 4 existing Stage 1/2 backfill jobs already use). */
    public void runMarketTransformationSequencesBackfill() {
        tracker.run(BACKFILL_JOB_NAME, orchestrator::backfill, "market.transformation_sequences", "computed_at");
    }
}
