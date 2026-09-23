package com.alphagraph.discovery.convergence;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs at 19:40 IST - live-verified against the real {@code CronMonitoringRepository.JOB_SCHEDULES}
 * map: the latest of all 6 real dependencies (Market/Ownership/Financial/Sector/Capital-Allocation
 * Stage 3 + {@code risk-contradiction-inflection}) is {@code sector-transformation-sequences} at
 * 19:33; nothing else is scheduled before 20:00 ({@code corporate-signal}), matching the
 * established "+6-8 min after the last real dependency" convention every prior domain's own Stage
 * 3 job used.
 *
 * <p>No backfill method is exposed here at all - see {@link DiscoveryConvergenceOrchestrator}'s
 * own javadoc for why (Financial Stage 3's unresolved publication-date gap), same "no scheduler
 * trigger method, no registry entry" posture {@code FinancialTransformationSequenceScheduler}
 * already established for the identical reason.
 */
@Component
public class DiscoveryConvergenceScheduler {

    private static final String CRON_740PM_IST = "0 40 19 * * *";
    private static final String JOB_NAME = "discovery-convergence-detection";

    private final DiscoveryConvergenceOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public DiscoveryConvergenceScheduler(DiscoveryConvergenceOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_740PM_IST, zone = "Asia/Kolkata")
    public void runConvergenceDetection() {
        tracker.run(JOB_NAME, orchestrator::run, "discovery.convergence_snapshots", "computed_at");
    }
}
