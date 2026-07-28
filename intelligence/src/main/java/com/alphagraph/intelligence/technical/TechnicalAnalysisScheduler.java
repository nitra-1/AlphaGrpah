package com.alphagraph.intelligence.technical;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 30 minutes after market data's 6 PM pipeline (docs/002_Engine_Architecture.md §6 covers
 * the ETL Download->Calculate flow; this is a separate windowed-computation flow, not an ETL
 * pipeline, so it isn't registered as a {@code ScheduledPipeline} - there's no Collector/Parser
 * stage here, just a read of already-loaded market data). The 30-minute buffer assumes the daily
 * bhavdata pipeline has finished loading before this runs; not coordinated more tightly than that
 * yet.
 */
@Component
public class TechnicalAnalysisScheduler {

    private static final String CRON_630PM_IST = "0 30 18 * * *";

    private final TechnicalAnalysisOrchestrator orchestrator;

    public TechnicalAnalysisScheduler(TechnicalAnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_630PM_IST, zone = "Asia/Kolkata")
    public void runDailyTechnicalAnalysis() {
        orchestrator.runForAllInstruments();
    }
}
