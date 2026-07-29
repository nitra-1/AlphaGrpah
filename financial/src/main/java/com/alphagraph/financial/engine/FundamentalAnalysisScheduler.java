package com.alphagraph.financial.engine;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 40 minutes after the 6 PM ETL cron, after financial-quarterly-results (Module 1.3) has had
 * time to finish loading. Not a {@code ScheduledPipeline}: there's no Collector/Parser stage here,
 * just a read of already-loaded financial_results - same reasoning as
 * intelligence.technical.TechnicalAnalysisScheduler (Module 1.5).
 */
@Component
public class FundamentalAnalysisScheduler {

    private static final String CRON_640PM_IST = "0 40 18 * * *";

    private final FundamentalAnalysisOrchestrator orchestrator;

    public FundamentalAnalysisScheduler(FundamentalAnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_640PM_IST, zone = "Asia/Kolkata")
    public void runDailyFundamentalAnalysis() {
        orchestrator.runForAllInstruments();
    }
}
