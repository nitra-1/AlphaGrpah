package com.alphagraph.corporate.events;

import com.alphagraph.corporate.knowledge.DocumentConsumerCheckpointWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Runs {@link CorporateEventEngine} over every document {@link KnowledgeDocumentReader} finds in
 * KNOWLEDGE_EXTRACTED status without a {@link KnowledgeDocumentReader#CONSUMER} checkpoint yet,
 * persists whatever events it detects via {@link CorporateEventWriter}, then marks the checkpoint -
 * whether or not any events were actually found, since "this document describes no classifiable
 * event" is a real, final outcome (most routine filings match none of the 13 categories), not a
 * reason to re-check it forever.
 */
@Component
public class EventExtractionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EventExtractionOrchestrator.class);

    private final KnowledgeDocumentReader documentReader;
    private final CorporateEventEngine engine;
    private final CorporateEventWriter eventWriter;
    private final DocumentConsumerCheckpointWriter checkpointWriter;

    public EventExtractionOrchestrator(
        KnowledgeDocumentReader documentReader, CorporateEventEngine engine,
        CorporateEventWriter eventWriter, DocumentConsumerCheckpointWriter checkpointWriter
    ) {
        this.documentReader = documentReader;
        this.engine = engine;
        this.eventWriter = eventWriter;
        this.checkpointWriter = checkpointWriter;
    }

    public void extractAllPending() {
        List<KnowledgeDocument> pending = documentReader.findUnprocessed();

        int documentsWithEvents = 0;
        int totalEvents = 0;
        int failed = 0;
        for (KnowledgeDocument document : pending) {
            try {
                int eventCount = extractOne(document);
                totalEvents += eventCount;
                if (eventCount > 0) {
                    documentsWithEvents++;
                }
            } catch (Exception e) {
                log.error("Failed to classify events for document {} (symbol={}): {}",
                    document.id(), document.symbol(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("Event classification: {} events found across {} documents ({} of {} documents had events, {} failed)",
            totalEvents, pending.size(), documentsWithEvents, pending.size(), failed);
    }

    private int extractOne(KnowledgeDocument document) {
        List<ExtractedEvent> events = engine.classify(document.knowledge());
        Instant extractedAt = Instant.now();

        for (ExtractedEvent event : events) {
            eventWriter.write(document.id(), document.instrumentId(), document.symbol(), event, CorporateEventEngine.RULE_VERSION, extractedAt);
        }

        checkpointWriter.markProcessed(document.id(), KnowledgeDocumentReader.CONSUMER);
        return events.size();
    }
}
