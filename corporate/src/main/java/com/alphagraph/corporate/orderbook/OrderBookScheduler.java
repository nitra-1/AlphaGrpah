package com.alphagraph.corporate.orderbook;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 15 minutes after {@code EventExtractionScheduler} (Module 2.3, 18:45 IST) - both engines
 * read the same KNOWLEDGE_EXTRACTED corpus independently, so their relative order doesn't matter
 * for correctness, but staggering keeps the two from contending for the same documents at once.
 */
@Component
public class OrderBookScheduler {

    private static final String CRON_7PM_IST = "0 0 19 * * *";
    private static final String JOB_NAME = "order-book";

    private final OrderBookOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public OrderBookScheduler(OrderBookOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_7PM_IST, zone = "Asia/Kolkata")
    public void runOrderBookUpdate() {
        tracker.run(JOB_NAME, orchestrator::runOrderBookUpdate, "corporate.order_book_snapshots", "computed_at");
    }
}
