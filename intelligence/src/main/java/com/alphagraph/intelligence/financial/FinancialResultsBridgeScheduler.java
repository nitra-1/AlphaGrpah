package com.alphagraph.intelligence.financial;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 5 minutes after Knowledge Extraction (18:30 IST, produces the facts this bridge reads) and
 * 5 minutes before Fundamental Analysis (18:40 IST, consumes {@code financial.financial_results}) -
 * so a Financial Results filing extracted today can feed same-day fundamental scoring. Not a
 * {@code ScheduledPipeline}: same reasoning as every other intelligence-bridging scheduler
 * ({@code InstitutionalAnalysisScheduler}, {@code TechnicalAnalysisScheduler}).
 */
@Component
public class FinancialResultsBridgeScheduler {

    private static final String CRON_635PM_IST = "0 35 18 * * *";
    private static final String JOB_NAME = "financial-results-bridge";

    private final FinancialResultsBridgeOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public FinancialResultsBridgeScheduler(FinancialResultsBridgeOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_635PM_IST, zone = "Asia/Kolkata")
    public void runDailyFinancialResultsBridge() {
        tracker.run(JOB_NAME, orchestrator::fillGapsFromExtractedFacts, "financial.financial_results", "created_at");
    }
}
