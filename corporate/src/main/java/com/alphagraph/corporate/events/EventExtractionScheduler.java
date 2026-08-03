package com.alphagraph.corporate.events;

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

    private final EventExtractionOrchestrator orchestrator;

    public EventExtractionScheduler(EventExtractionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_645PM_IST, zone = "Asia/Kolkata")
    public void runEventExtraction() {
        orchestrator.extractAllPending();
    }
}
