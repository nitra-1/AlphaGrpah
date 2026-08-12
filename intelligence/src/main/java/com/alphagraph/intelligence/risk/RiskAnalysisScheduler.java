package com.alphagraph.intelligence.risk;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs at 7 PM IST - after the 6:50 PM Institutional run (Module 1.7) and the 6:55 PM Sector run
 * (Module 1.8), since Risk aggregates Technical, Fundamental, and Institutional scores and needs
 * all three to have already finished computing for the day. Not a {@code ScheduledPipeline}: same
 * reasoning as every prior intelligence.*.Scheduler (Modules 1.5-1.8).
 */
@Component
public class RiskAnalysisScheduler {

    private static final String CRON_7PM_IST = "0 0 19 * * *";
    private static final String JOB_NAME = "risk-analysis";

    private final RiskAnalysisOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public RiskAnalysisScheduler(RiskAnalysisOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_7PM_IST, zone = "Asia/Kolkata")
    public void runDailyRiskAnalysis() {
        tracker.run(JOB_NAME, orchestrator::runForAllInstruments, "risk.risk_scores", "computed_at");
    }
}
