package com.alphagraph.market.transformation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs at 18:32 IST, right after {@code technical-analysis} (18:30) since it wants the same freshly-updated daily bar data. */
@Component
public class MarketAccumulationScheduler {

    private static final String CRON_632PM_IST = "0 32 18 * * *";
    private static final String JOB_NAME = "market-accumulation-evidence";
    private static final String BACKFILL_JOB_NAME = "market-accumulation-evidence-backfill";

    private final MarketAccumulationOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public MarketAccumulationScheduler(MarketAccumulationOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_632PM_IST, zone = "Asia/Kolkata")
    public void runMarketAccumulationEvidence() {
        tracker.run(JOB_NAME, orchestrator::run, "market.transformation_evidence", "computed_at");
    }

    /** One-off historical catch-up, manually triggered only (no {@code @Scheduled} - never fires on its own, see claude.md). */
    public void runMarketAccumulationEvidenceBackfill() {
        tracker.run(BACKFILL_JOB_NAME, orchestrator::backfill, "market.transformation_evidence", "computed_at");
    }
}
