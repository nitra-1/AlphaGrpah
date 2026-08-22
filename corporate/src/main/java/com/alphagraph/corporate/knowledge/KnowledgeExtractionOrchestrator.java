package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.relationships.RelationshipBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Runs the full three-stage pipeline over every document {@link PendingKnowledgeDocumentReader}
 * finds in PROCESSED status: Stage 1 ({@link DocumentIntelligenceEngine}) classifies the document;
 * {@link DocumentRouter} dispatches to whichever Stage 2 {@link DocumentExtractor}s apply and
 * collects their facts; {@link CanonicalKnowledgeWriter} persists both. {@link RelationshipBuilder}
 * (Module 2.7) then runs synchronously over the same facts, resolving any entity/relationship
 * tags they carry into {@code knowledge.relationship} edges - the graph's only writer, right after
 * the facts it reads become durable. The document then advances to KNOWLEDGE_EXTRACTED - the last
 * shared pipeline stage. From here, every downstream rule engine (Corporate Event Engine, Order
 * Book Engine, ...) reads this document independently, tracking its own progress via
 * {@code corporate.document_consumer_checkpoints} rather than any further document-status change.
 */
@Component
public class KnowledgeExtractionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractionOrchestrator.class);

    private final PendingKnowledgeDocumentReader documentReader;
    private final DocumentIntelligenceEngine classificationEngine;
    private final DocumentRouter router;
    private final CanonicalKnowledgeWriter writer;
    private final RelationshipBuilder relationshipBuilder;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeExtractionOrchestrator(
        PendingKnowledgeDocumentReader documentReader, DocumentIntelligenceEngine classificationEngine,
        DocumentRouter router, CanonicalKnowledgeWriter writer, RelationshipBuilder relationshipBuilder,
        JdbcTemplate jdbcTemplate
    ) {
        this.documentReader = documentReader;
        this.classificationEngine = classificationEngine;
        this.router = router;
        this.writer = writer;
        this.relationshipBuilder = relationshipBuilder;
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

        relationshipBuilder.expandCompetitorGroups();

        log.info("Knowledge extraction: {} documents processed, {} failed (of {})", succeeded, failed, pending.size());
    }

    /**
     * Extracts a single document immediately, regardless of its current status - used by the admin
     * news-review "keep" decision (Module 2.6 pre-filter retrofit): an admin promoting a
     * PENDING_REVIEW article should see it extracted right away, not wait for the next scheduled
     * PROCESSED-only {@link #extractAllPending()} run.
     */
    public void extractDocument(UUID documentId) {
        PendingKnowledgeDocument document = documentReader.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("No document with id " + documentId));
        extractOne(document);
    }

    private void extractOne(PendingKnowledgeDocument document) {
        DocumentClassification classification = classificationEngine.classify(document.extractedText(), document.source());
        writer.writeClassification(document.id(), classification);

        DocumentContext context = new DocumentContext(
            document.id(), document.instrumentId(), document.symbol(), document.extractedText(), classification
        );
        List<ExtractedFact> facts = router.route(context);
        writer.writeFacts(document.id(), facts);
        relationshipBuilder.build(document.id(), document.symbol(), facts);

        jdbcTemplate.update(
            "UPDATE corporate.documents SET status = 'KNOWLEDGE_EXTRACTED' WHERE id = ?", document.id()
        );
    }
}
