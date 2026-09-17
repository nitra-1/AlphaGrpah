package com.alphagraph.financial.transformation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs at 18:37 IST, right after {@code financial-results-bridge} (18:35). */
@Component
public class FinancialTransformationScheduler {

    private static final String CRON_637PM_IST = "0 37 18 * * *";
    private static final String JOB_NAME = "financial-results-comparision-fetch";

    private final FinancialTransformationOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public FinancialTransformationScheduler(FinancialTransformationOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_637PM_IST, zone = "Asia/Kolkata")
    public void runFinancialResultsComparisionFetch() {
        tracker.run(JOB_NAME, orchestrator::run, "financial.transformation_evidence", "computed_at");
    }
}
