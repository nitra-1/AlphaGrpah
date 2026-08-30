package com.alphagraph.ownership.interpretation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs after {@code deal-materiality-scoring} (18:20 IST) so today's real materiality scores are
 * already in {@code ownership.deal_materiality} by the time this job interprets them.
 */
@Component
public class InstitutionalInterpretationScheduler {

    private static final String CRON_630PM_IST = "0 30 18 * * *";
    private static final String JOB_NAME = "institutional-interpretation";

    private final InstitutionalInterpretationOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public InstitutionalInterpretationScheduler(InstitutionalInterpretationOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_630PM_IST, zone = "Asia/Kolkata")
    public void runInstitutionalInterpretation() {
        tracker.run(JOB_NAME, orchestrator::run, "ownership.institutional_interpretations", "computed_at");
    }
}
