package com.alphagraph.financial.transformation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs at 19:22 IST, after every Stage 1 job that could still be feeding it that day. */
@Component
public class FinancialInflectionScheduler {

    private static final String CRON_722PM_IST = "0 22 19 * * *";
    private static final String JOB_NAME = "financial-inflection";

    private final FinancialInflectionOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public FinancialInflectionScheduler(FinancialInflectionOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_722PM_IST, zone = "Asia/Kolkata")
    public void runFinancialInflection() {
        tracker.run(JOB_NAME, orchestrator::run, "financial.inflection_states", "computed_at");
    }
}
