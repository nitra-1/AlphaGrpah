package com.alphagraph.corporate.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs {@link DocumentIntelligenceEngine} over every document {@link PendingKnowledgeDocumentReader}
 * finds in PROCESSED status, persists the canonical facts/topics/summary via
 * {@link CanonicalKnowledgeWriter}, then advances the document to KNOWLEDGE_EXTRACTED - the last
 * shared pipeline stage. From here, every downstream rule engine (Corporate Event Engine, Order
 * Book Engine, ...) reads this document independently, tracking its own progress via
 * {@code corporate.document_consumer_checkpoints} rather than any further document-status change.
 */
@Component
public class KnowledgeExtractionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractionOrchestrator.class);

    private final PendingKnowledgeDocumentReader documentReader;
    private final DocumentIntelligenceEngine engine;
    private final CanonicalKnowledgeWriter writer;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeExtractionOrchestrator(
        PendingKnowledgeDocumentReader documentReader, DocumentIntelligenceEngine engine,
        CanonicalKnowledgeWriter writer, JdbcTemplate jdbcTemplate
    ) {
        this.documentReader = documentReader;
        this.engine = engine;
        this.writer = writer;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void extractAllPending() {
        List<PendingKnowledgeDocument> pending = documentReader.findProcessed();

        int succeeded = 0;
        int failed = 0;
        for (PendingKnowledgeDocument document : pending) {
            try {
                extractOne(document);
                succeeded++;
            } catch (Exception e) {
                log.error("Failed to extract knowledge for document {} (symbol={}): {}",
                    document.id(), document.symbol(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("Knowledge extraction: {} documents processed, {} failed (of {})", succeeded, failed, pending.size());
    }

    private void extractOne(PendingKnowledgeDocument document) {
        CanonicalExtraction extraction = engine.extract(document.extractedText());
        writer.write(document.id(), extraction);

        jdbcTemplate.update(
            "UPDATE corporate.documents SET status = 'KNOWLEDGE_EXTRACTED' WHERE id = ?", document.id()
        );
    }
}
