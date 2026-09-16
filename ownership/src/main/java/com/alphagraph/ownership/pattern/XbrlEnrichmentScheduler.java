package com.alphagraph.ownership.pattern;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs 5 minutes after {@code ownership-shareholding-pattern} (18:00 IST) so today's freshly captured XBRL URLs are already in {@code ownership.shareholding_xbrl_urls}. */
@Component
public class XbrlEnrichmentScheduler {

    private static final String CRON_605PM_IST = "0 5 18 * * *";
    private static final String JOB_NAME = "xbrl-shareholding-enrichment";

    private final XbrlEnrichmentOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public XbrlEnrichmentScheduler(XbrlEnrichmentOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_605PM_IST, zone = "Asia/Kolkata")
    public void runXbrlShareholdingEnrichment() {
        // updated_at, not created_at - this job UPDATEs existing rows (adding the XBRL sub-
        // category columns), it never inserts new ones, so only updated_at (bumped by
        // shareholding_pattern's own trg_shareholding_pattern_updated_at trigger) reflects this
        // run's real writes.
        tracker.run(JOB_NAME, orchestrator::run, "ownership.shareholding_pattern", "updated_at");
    }
}
