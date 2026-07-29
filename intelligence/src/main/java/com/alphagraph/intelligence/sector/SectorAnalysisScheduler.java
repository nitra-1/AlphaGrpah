package com.alphagraph.intelligence.sector;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 55 minutes after the 6 PM ETL cron, after market's daily pipeline has had time to finish
 * loading. Not a {@code ScheduledPipeline}: same reasoning as
 * intelligence.technical.TechnicalAnalysisScheduler (Module 1.5).
 */
@Component
public class SectorAnalysisScheduler {

    private static final String CRON_655PM_IST = "0 55 18 * * *";

    private final SectorAnalysisOrchestrator orchestrator;

    public SectorAnalysisScheduler(SectorAnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_655PM_IST, zone = "Asia/Kolkata")
    public void runDailySectorAnalysis() {
        orchestrator.runForAllSectors();
    }
}
