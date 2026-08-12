package com.alphagraph.financial.engine;

import com.alphagraph.common.monitoring.JobRunTracker;
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
    private static final String JOB_NAME = "fundamental-analysis";

    private final FundamentalAnalysisOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public FundamentalAnalysisScheduler(FundamentalAnalysisOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_640PM_IST, zone = "Asia/Kolkata")
    public void runDailyFundamentalAnalysis() {
        tracker.run(JOB_NAME, orchestrator::runForAllInstruments, "financial.fundamental_scores", "computed_at");
    }
}
