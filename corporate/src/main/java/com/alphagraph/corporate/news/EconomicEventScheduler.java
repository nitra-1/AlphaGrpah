package com.alphagraph.corporate.news;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 3 minutes after {@code knowledge-extraction} (18:30 IST) - its own dependency is the
 * KNOWLEDGE_EXTRACTED corpus that job produces, independent of {@link NewsCatalystScheduler}
 * (19:30 IST), which reads the same corpus for a different, narrower purpose (tracked-instrument-
 * only catalyst scoring). Live-verified free slot: nothing else scheduled between 18:30 and
 * {@code corporate-event-extraction} at 18:45.
 */
@Component
public class EconomicEventScheduler {

    private static final String CRON_1833PM_IST = "0 33 18 * * *";
    private static final String JOB_NAME = "economic-event-detection";

    private final EconomicEventOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public EconomicEventScheduler(EconomicEventOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_1833PM_IST, zone = "Asia/Kolkata")
    public void runEconomicEventDetection() {
        tracker.run(JOB_NAME, orchestrator::run, "corporate.economic_events", "computed_at");
    }
}
