package com.alphagraph.decision.engine;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs after every upstream scheduler that feeds a domain score has had its chance to run for the
 * day - the latest is {@code corporate.signal.CorporateSignalScheduler} at 20:00 IST (moved from
 * 19:45 on 2026-08-22), itself running after {@code corporate.news.NewsCatalystScheduler} at 19:30
 * IST, the News Catalyst score it depends on. 21:45 IST leaves 1h45m after the last of them.
 */
@Component
public class DecisionScoringScheduler {

    private static final String CRON_945PM_IST = "0 45 21 * * *";
    private static final String JOB_NAME = "decision-scoring";

    private final DecisionScoringOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public DecisionScoringScheduler(DecisionScoringOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_945PM_IST, zone = "Asia/Kolkata")
    public void runDecisionScoringUpdate() {
        tracker.run(JOB_NAME, orchestrator::recomputeAllInstruments, "decision.decision_scores", "computed_at");
    }
}
