package com.alphagraph.sector.transformation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs at 19:26 IST, after {@code capital-allocation-inflection} (19:24) and before {@code news-catalyst} (19:30); depends on {@code sector-context-evidence} (18:57). */
@Component
public class SectorInflectionScheduler {

    private static final String CRON_726PM_IST = "0 26 19 * * *";
    private static final String JOB_NAME = "sector-inflection";

    private final SectorInflectionOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public SectorInflectionScheduler(SectorInflectionOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_726PM_IST, zone = "Asia/Kolkata")
    public void runSectorInflection() {
        tracker.run(JOB_NAME, orchestrator::run, "sector.inflection_states", "computed_at");
    }
}
