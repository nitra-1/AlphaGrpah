package com.alphagraph.corporate.events;

import com.alphagraph.common.monitoring.JobRunTracker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 15 minutes after {@code KnowledgeExtractionScheduler} (Module 2.2, 18:30 IST), giving that
 * scheduler time to finish canonical extraction before event classification starts. Not a
 * {@code ScheduledPipeline} - same reasoning as {@code KnowledgeExtractionScheduler}: this isn't
 * source ingestion, it's a derived computation over already-collected data.
 */
@Component
public class EventExtractionScheduler {

    private static final String CRON_645PM_IST = "0 45 18 * * *";
    private static final String JOB_NAME = "corporate-event-extraction";

    private final EventExtractionOrchestrator orchestrator;
    private final JobRunTracker tracker;

    public EventExtractionScheduler(EventExtractionOrchestrator orchestrator, JobRunTracker tracker) {
        this.orchestrator = orchestrator;
        this.tracker = tracker;
    }

    @Scheduled(cron = CRON_645PM_IST, zone = "Asia/Kolkata")
    public void runEventExtraction() {
        tracker.run(JOB_NAME, orchestrator::extractAllPending, "corporate.corporate_events", "extracted_at");
    }
}
