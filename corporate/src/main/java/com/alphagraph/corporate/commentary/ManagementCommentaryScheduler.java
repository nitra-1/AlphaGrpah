package com.alphagraph.corporate.commentary;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 15 minutes after {@code OrderBookScheduler} (Module 2.4, 19:00 IST) - both engines read
 * the same KNOWLEDGE_EXTRACTED corpus independently, so their relative order doesn't matter for
 * correctness, but staggering keeps the two from contending for the same documents at once.
 */
@Component
public class ManagementCommentaryScheduler {

    private static final String CRON_915PM_IST = "0 15 19 * * *";

    private final ManagementCommentaryOrchestrator orchestrator;

    public ManagementCommentaryScheduler(ManagementCommentaryOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_915PM_IST, zone = "Asia/Kolkata")
    public void runManagementCommentaryUpdate() {
        orchestrator.runManagementCommentaryUpdate();
    }
}
