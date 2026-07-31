package com.alphagraph.corporate.events;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 15 minutes after {@code DocumentProcessingScheduler} (Module 2.1, 18:15 IST), giving that
 * scheduler time to finish chunking/embedding before event extraction starts. Not a
 * {@code ScheduledPipeline} - same reasoning as {@code DocumentProcessingScheduler}: this isn't
 * source ingestion, it's a derived computation over already-collected data.
 */
@Component
public class EventExtractionScheduler {

    private static final String CRON_630PM_IST = "0 30 18 * * *";

    private final EventExtractionOrchestrator orchestrator;

    public EventExtractionScheduler(EventExtractionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_630PM_IST, zone = "Asia/Kolkata")
    public void runEventExtraction() {
        orchestrator.extractAllPending();
    }
}
