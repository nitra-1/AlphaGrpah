package com.alphagraph.corporate.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs the full three-stage pipeline over every document {@link PendingKnowledgeDocumentReader}
 * finds in PROCESSED status: Stage 1 ({@link DocumentIntelligenceEngine}) classifies the document;
 * {@link DocumentRouter} dispatches to whichever Stage 2 {@link DocumentExtractor}s apply and
 * collects their facts; {@link CanonicalKnowledgeWriter} persists both. The document then advances
 * to KNOWLEDGE_EXTRACTED - the last shared pipeline stage. From here, every downstream rule engine
 * (Corporate Event Engine, Order Book Engine, ...) reads this document independently, tracking its
 * own progress via {@code corporate.document_consumer_checkpoints} rather than any further
 * document-status change.
 */
@Component
public class KnowledgeExtractionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractionOrchestrator.class);

    private final PendingKnowledgeDocumentReader documentReader;
    private final DocumentIntelligenceEngine classificationEngine;
    private final DocumentRouter router;
    private final CanonicalKnowledgeWriter writer;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeExtractionOrchestrator(
        PendingKnowledgeDocumentReader documentReader, DocumentIntelligenceEngine classificationEngine,
        DocumentRouter router, CanonicalKnowledgeWriter writer, JdbcTemplate jdbcTemplate
    ) {
        this.documentReader = documentReader;
        this.classificationEngine = classificationEngine;
        this.router = router;
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
        DocumentClassification classification = classificationEngine.classify(document.extractedText());
        writer.writeClassification(document.id(), classification);

        DocumentContext context = new DocumentContext(
            document.id(), document.instrumentId(), document.symbol(), document.extractedText(), classification
        );
        List<ExtractedFact> facts = router.route(context);
        writer.writeFacts(document.id(), facts);

        jdbcTemplate.update(
            "UPDATE corporate.documents SET status = 'KNOWLEDGE_EXTRACTED' WHERE id = ?", document.id()
        );
    }
}
