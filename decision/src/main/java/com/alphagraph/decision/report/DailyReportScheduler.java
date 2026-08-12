package com.alphagraph.decision.report;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs after DecisionScoringScheduler (21:45 IST) and every corporate-domain scheduler for the day, so the day's rank movements and events/guidance/news are all on record before the digest is built. */
@Component
public class DailyReportScheduler {

    private static final String CRON_10PM_IST = "0 0 22 * * *";
    private static final String JOB_NAME = "daily-ai-report";

    private final DailyReportOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public DailyReportScheduler(DailyReportOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_10PM_IST, zone = "Asia/Kolkata")
    public void runDailyReportGeneration() {
        tracker.run(JOB_NAME, orchestrator::generateForToday, "decision.daily_reports", "generated_at");
    }
}
