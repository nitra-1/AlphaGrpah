package com.alphagraph.corporate.processing;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 15 minutes after the 6PM Exchange Announcements collection pipeline, giving that pipeline
 * time to finish downloading PDFs before processing starts. Not a {@code ScheduledPipeline} - same
 * reasoning as every {@code intelligence.*.Scheduler} in Phase 1 (e.g.
 * technical.TechnicalAnalysisScheduler, Module 1.5): this isn't a source ingestion, it's a derived
 * computation over already-collected data.
 */
@Component
public class DocumentProcessingScheduler {

    private static final String CRON_615PM_IST = "0 15 18 * * *";

    private final DocumentProcessingOrchestrator orchestrator;

    public DocumentProcessingScheduler(DocumentProcessingOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_615PM_IST, zone = "Asia/Kolkata")
    public void runDocumentProcessing() {
        orchestrator.processAllPending();
    }
}
