package com.alphagraph.corporate.knowledge;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs 15 minutes after {@code DocumentProcessingScheduler} (Module 2.1, 18:15 IST), giving that
 * scheduler time to finish chunking/embedding before canonical knowledge extraction starts. Runs
 * before every downstream rule-engine scheduler (Corporate Event Engine, Order Book Engine), since
 * they depend on this stage's output.
 */
@Component
public class KnowledgeExtractionScheduler {

    private static final String CRON_630PM_IST = "0 30 18 * * *";

    private final KnowledgeExtractionOrchestrator orchestrator;

    public KnowledgeExtractionScheduler(KnowledgeExtractionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = CRON_630PM_IST, zone = "Asia/Kolkata")
    public void runKnowledgeExtraction() {
        orchestrator.extractAllPending();
    }
}
