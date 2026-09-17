package com.alphagraph.intelligence.sectorcontext;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs at 18:57 IST, after both {@code market-accumulation-evidence} (18:32, source of PRICE_RETURN_20D) and {@code sector-analysis} (18:55, source of relative_strength) have had time to complete. */
@Component
public class SectorContextScheduler {

    private static final String CRON_657PM_IST = "0 57 18 * * *";
    private static final String JOB_NAME = "sector-context-evidence";

    private final SectorContextOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public SectorContextScheduler(SectorContextOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_657PM_IST, zone = "Asia/Kolkata")
    public void runSectorContextEvidence() {
        tracker.run(JOB_NAME, orchestrator::run, "sector.transformation_evidence", "computed_at");
    }
}
