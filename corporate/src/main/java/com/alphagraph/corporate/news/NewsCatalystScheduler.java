package com.alphagraph.corporate.news;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 15 minutes after {@code ManagementCommentaryScheduler} (Module 2.5, 19:15 IST) - every
 * Stage 2 consumer reads the same KNOWLEDGE_EXTRACTED corpus independently, so relative order
 * doesn't matter for correctness, but staggering keeps them from contending for the same
 * documents at once.
 */
@Component
public class NewsCatalystScheduler {

    private static final String CRON_930PM_IST = "0 30 19 * * *";

    private final NewsCatalystOrchestrator orchestrator;

    public NewsCatalystScheduler(NewsCatalystOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_930PM_IST, zone = "Asia/Kolkata")
    public void runNewsCatalystUpdate() {
        orchestrator.runNewsCatalystUpdate();
    }
}
