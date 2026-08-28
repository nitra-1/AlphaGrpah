package com.alphagraph.ownership.deals;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs after {@code market-discovery-price-backfill} (18:10 IST) so today's freshly backfilled/
 * captured prices are already in {@code market.discovered_prices} by the time this job looks for
 * unscored deals.
 */
@Component
public class DealMaterialityScoringScheduler {

    private static final String CRON_620PM_IST = "0 20 18 * * *";
    private static final String JOB_NAME = "deal-materiality-scoring";

    private final DealMaterialityScoringOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public DealMaterialityScoringScheduler(DealMaterialityScoringOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_620PM_IST, zone = "Asia/Kolkata")
    public void runDealMaterialityScoring() {
        tracker.run(JOB_NAME, orchestrator::scorePendingDeals, "ownership.deal_materiality", "computed_at");
    }
}
