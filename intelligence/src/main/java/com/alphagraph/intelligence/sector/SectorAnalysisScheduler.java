package com.alphagraph.intelligence.sector;

import com.alphagraph.common.monitoring.JobRunTracker;
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
    private static final String JOB_NAME = "sector-analysis";

    private final SectorAnalysisOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public SectorAnalysisScheduler(SectorAnalysisOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_655PM_IST, zone = "Asia/Kolkata")
    public void runDailySectorAnalysis() {
        tracker.run(JOB_NAME, orchestrator::runForAllSectors, "sector.sector_scores", "computed_at");
    }
}
