package com.alphagraph.intelligence.institutional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 50 minutes after the 6 PM ETL cron, after both market's daily pipeline and ownership's
 * shareholding/bulk-deals pipelines have had time to finish loading. Not a
 * {@code ScheduledPipeline}: same reasoning as intelligence.technical.TechnicalAnalysisScheduler
 * (Module 1.5) and financial.engine.FundamentalAnalysisScheduler (Module 1.6).
 */
@Component
public class InstitutionalAnalysisScheduler {

    private static final String CRON_650PM_IST = "0 50 18 * * *";

    private final InstitutionalAnalysisOrchestrator orchestrator;

    public InstitutionalAnalysisScheduler(InstitutionalAnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_650PM_IST, zone = "Asia/Kolkata")
    public void runDailyInstitutionalAnalysis() {
        orchestrator.runForAllInstruments();
    }
}
