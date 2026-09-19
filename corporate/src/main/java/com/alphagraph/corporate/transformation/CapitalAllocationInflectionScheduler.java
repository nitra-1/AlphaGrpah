package com.alphagraph.corporate.transformation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs at 19:24 IST, after {@code financial-inflection} (19:22) and before {@code news-catalyst} (19:30). */
@Component
public class CapitalAllocationInflectionScheduler {

    private static final String CRON_724PM_IST = "0 24 19 * * *";
    private static final String JOB_NAME = "capital-allocation-inflection";

    private final CapitalAllocationInflectionOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public CapitalAllocationInflectionScheduler(CapitalAllocationInflectionOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_724PM_IST, zone = "Asia/Kolkata")
    public void runCapitalAllocationInflection() {
        tracker.run(JOB_NAME, orchestrator::run, "corporate.inflection_states", "computed_at");
    }
}
