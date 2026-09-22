package com.alphagraph.financial.transformation;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs at 19:28 IST, 6 minutes after {@code financial-inflection} (19:22) - confirmed free against
 * the real, current {@code CronMonitoringRepository.JOB_SCHEDULES} map (between
 * {@code market-transformation-sequences} 19:27 and {@code risk-contradiction-inflection} 19:29).
 *
 * <p><b>Deliberately exposes only {@code run()}</b> - unlike every prior Stage 3 domain, there is
 * no {@code runFinancialTransformationSequencesBackfill()} method here and no backfill job name.
 * {@code FinancialTransformationSequenceOrchestrator.backfill()} is not point-in-time safe for
 * Financial (see that class's own javadoc) and must not be reachable via the admin API until a real
 * publication-date field exists for Financial evidence.
 */
@Component
public class FinancialTransformationSequenceScheduler {

    private static final String CRON_728PM_IST = "0 28 19 * * *";
    private static final String JOB_NAME = "financial-transformation-sequences";

    private final FinancialTransformationSequenceOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public FinancialTransformationSequenceScheduler(FinancialTransformationSequenceOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_728PM_IST, zone = "Asia/Kolkata")
    public void runFinancialTransformationSequences() {
        tracker.run(JOB_NAME, orchestrator::run, "financial.transformation_sequences", "computed_at");
    }
}
