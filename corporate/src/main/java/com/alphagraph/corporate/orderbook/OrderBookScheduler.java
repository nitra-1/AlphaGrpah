package com.alphagraph.corporate.orderbook;

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

    private final OrderBookOrchestrator orchestrator;

    public OrderBookScheduler(OrderBookOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_7PM_IST, zone = "Asia/Kolkata")
    public void runOrderBookUpdate() {
        orchestrator.runOrderBookUpdate();
    }
}
