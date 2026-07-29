package com.alphagraph.intelligence.risk;

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

    private final RiskAnalysisOrchestrator orchestrator;

    public RiskAnalysisScheduler(RiskAnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_7PM_IST, zone = "Asia/Kolkata")
    public void runDailyRiskAnalysis() {
        orchestrator.runForAllInstruments();
    }
}
