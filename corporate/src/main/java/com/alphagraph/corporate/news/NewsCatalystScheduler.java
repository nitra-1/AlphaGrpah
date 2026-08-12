package com.alphagraph.corporate.news;

import com.alphagraph.common.monitoring.JobRunTracker;
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
    private static final String JOB_NAME = "news-catalyst";

    private final NewsCatalystOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public NewsCatalystScheduler(NewsCatalystOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_930PM_IST, zone = "Asia/Kolkata")
    public void runNewsCatalystUpdate() {
        tracker.run(JOB_NAME, orchestrator::runNewsCatalystUpdate, "corporate.news_catalyst_scores", "computed_at");
    }
}
