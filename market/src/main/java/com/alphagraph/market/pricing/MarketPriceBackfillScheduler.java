package com.alphagraph.market.pricing;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 10 minutes after {@code ownership-bulk-block-deals} (18:00 IST) so today's newly
 * discovered candidates (see {@code ownership.deals.DiscoveryService}) are already in
 * {@code ownership.discovery_status} by the time this job looks for symbols needing backfill.
 */
@Component
public class MarketPriceBackfillScheduler {

    private static final String CRON_610PM_IST = "0 10 18 * * *";
    private static final String JOB_NAME = "market-discovery-price-backfill";

    private final MarketPriceBackfillOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public MarketPriceBackfillScheduler(MarketPriceBackfillOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_610PM_IST, zone = "Asia/Kolkata")
    public void runDiscoveryPriceBackfill() {
        tracker.run(JOB_NAME, orchestrator::backfillDiscoveryCandidates, "market.discovered_prices", "ingested_at");
    }
}
