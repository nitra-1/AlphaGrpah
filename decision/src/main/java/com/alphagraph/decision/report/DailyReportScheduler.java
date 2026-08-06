package com.alphagraph.decision.report;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs after DecisionScoringScheduler (21:45 IST) and every corporate-domain scheduler for the day, so the day's rank movements and events/guidance/news are all on record before the digest is built. */
@Component
public class DailyReportScheduler {

    private static final String CRON_10PM_IST = "0 0 22 * * *";

    private final DailyReportOrchestrator orchestrator;

    public DailyReportScheduler(DailyReportOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_10PM_IST, zone = "Asia/Kolkata")
    public void runDailyReportGeneration() {
        orchestrator.generateForToday();
    }
}
