package com.alphagraph.discovery.lifecycle;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs at 19:47 IST - live-verified against the real {@code CronMonitoringRepository.JOB_SCHEDULES}
 * map: {@code discovery-convergence-detection} (Stage 5's only real dependency) sits at 19:40,
 * nothing else is scheduled before {@code corporate-signal} at 20:00, matching the established
 * "+6-8 min after the last real dependency" convention every prior Stage 3/4 job used.
 *
 * <p>No backfill method exposed here at all - see {@link DiscoveryLifecycleOrchestrator}'s own
 * javadoc for why (Stage 4's own historical backfill is itself refused), same "no scheduler
 * trigger method, no registry entry" posture {@code DiscoveryConvergenceScheduler} already
 * established for the identical reason.
 */
@Component
public class DiscoveryLifecycleScheduler {

    private static final String CRON_747PM_IST = "0 47 19 * * *";
    private static final String JOB_NAME = "lifecycle-classification";

    private final DiscoveryLifecycleOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public DiscoveryLifecycleScheduler(DiscoveryLifecycleOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_747PM_IST, zone = "Asia/Kolkata")
    public void runLifecycleClassification() {
        tracker.run(JOB_NAME, orchestrator::run, "discovery.lifecycle_snapshots", "computed_at");
    }
}
