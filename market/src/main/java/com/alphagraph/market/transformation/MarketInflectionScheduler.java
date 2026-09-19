package com.alphagraph.market.transformation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs at 19:20 IST, after every Stage 1 job that could still be feeding it that day (the latest, {@code ownership-transformation}, completes by 19:10). */
@Component
public class MarketInflectionScheduler {

    private static final String CRON_720PM_IST = "0 20 19 * * *";
    private static final String JOB_NAME = "market-inflection";

    private final MarketInflectionOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public MarketInflectionScheduler(MarketInflectionOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_720PM_IST, zone = "Asia/Kolkata")
    public void runMarketInflection() {
        tracker.run(JOB_NAME, orchestrator::run, "market.inflection_states", "computed_at");
    }
}
