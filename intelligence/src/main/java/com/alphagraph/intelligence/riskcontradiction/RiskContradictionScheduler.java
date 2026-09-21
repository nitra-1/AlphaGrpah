package com.alphagraph.intelligence.riskcontradiction;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs at 19:29 IST, after every other Stage 2 job ({@code sector-inflection} 19:26 is the latest) and before {@code news-catalyst} (19:30). */
@Component
public class RiskContradictionScheduler {

    private static final String CRON_729PM_IST = "0 29 19 * * *";
    private static final String JOB_NAME = "risk-contradiction-inflection";

    private final RiskContradictionOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public RiskContradictionScheduler(RiskContradictionOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_729PM_IST, zone = "Asia/Kolkata")
    public void runRiskContradiction() {
        tracker.run(JOB_NAME, orchestrator::run, "risk.contradiction_states", "computed_at");
    }
}
