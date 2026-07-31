package com.alphagraph.corporate.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Runs {@link CorporateEventEngine} over every document {@link ProcessedDocumentReader} finds in
 * PROCESSED status, persists whatever events it detects via {@link CorporateEventWriter}, then
 * advances the document to EVENTS_EXTRACTED - whether or not any events were actually found,
 * since "this document describes no classifiable event" is a real, final outcome (most routine
 * filings match none of the 13 categories), not a reason to retry indefinitely.
 *
 * Reads only {@code corporate.documents.extracted_text} - never re-parses the original PDF at
 * extraction time, per docs/claude.md Module 2.2's "Never parse documents directly during
 * scoring" principle, which remains a valid design constraint even though Module 2.2 itself was
 * skipped as redundant with what Module 2.1 already built.
 */
@Component
public class EventExtractionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EventExtractionOrchestrator.class);

    private final ProcessedDocumentReader documentReader;
    private final CorporateEventEngine engine;
    private final CorporateEventWriter eventWriter;
    private final JdbcTemplate jdbcTemplate;

    public EventExtractionOrchestrator(
        ProcessedDocumentReader documentReader, CorporateEventEngine engine,
        CorporateEventWriter eventWriter, JdbcTemplate jdbcTemplate
    ) {
        this.documentReader = documentReader;
        this.engine = engine;
        this.eventWriter = eventWriter;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void extractAllPending() {
        List<ProcessedDocument> pending = documentReader.findProcessed();

        int documentsWithEvents = 0;
        int totalEvents = 0;
        int failed = 0;
        for (ProcessedDocument document : pending) {
            try {
                int eventCount = extractOne(document);
                totalEvents += eventCount;
                if (eventCount > 0) {
                    documentsWithEvents++;
                }
            } catch (Exception e) {
                log.error("Failed to extract events for document {} (symbol={}): {}",
                    document.id(), document.symbol(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("Event extraction: {} events found across {} documents ({} of {} documents had events, {} failed)",
            totalEvents, pending.size(), documentsWithEvents, pending.size(), failed);
    }

    private int extractOne(ProcessedDocument document) {
        List<ExtractedEvent> events = engine.extractEvents(document.extractedText());
        Instant extractedAt = Instant.now();

        for (ExtractedEvent event : events) {
            eventWriter.write(document.id(), document.instrumentId(), document.symbol(), event, CorporateEventEngine.PROMPT_VERSION, extractedAt);
        }

        jdbcTemplate.update(
            "UPDATE corporate.documents SET status = 'EVENTS_EXTRACTED' WHERE id = ?", document.id()
        );
        return events.size();
    }
}
