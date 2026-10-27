package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.DocumentSource;
import com.alphagraph.corporate.relationships.RelationshipBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
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
    private final DocumentRepeatDetector repeatDetector;
    private final AnnouncementTextBudget textBudget;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeExtractionOrchestrator(
        PendingKnowledgeDocumentReader documentReader, DocumentIntelligenceEngine classificationEngine,
        DocumentRouter router, CanonicalKnowledgeWriter writer, RelationshipBuilder relationshipBuilder,
        DocumentRepeatDetector repeatDetector, AnnouncementTextBudget textBudget, JdbcTemplate jdbcTemplate
    ) {
        this.documentReader = documentReader;
        this.classificationEngine = classificationEngine;
        this.router = router;
        this.writer = writer;
        this.relationshipBuilder = relationshipBuilder;
        this.repeatDetector = repeatDetector;
        this.textBudget = textBudget;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void extractAllPending() {
        List<PendingKnowledgeDocument> pending = documentReader.findProcessed();

        int succeeded = 0;
        int repeats = 0;
        int deferred = 0;
        int failed = 0;
        for (PendingKnowledgeDocument document : pending) {
            try {
                if (extractOne(document)) {
                    repeats++;
                } else {
                    succeeded++;
                }
            } catch (ExtractionDeferredException e) {
                // Not a failure: Gemini couldn't answer and Claude isn't to be spent on it. Nothing was
                // written for this document, so it simply stays PROCESSED and is retried next sweep.
                deferred++;
                log.warn("Deferred document {} (symbol={}) to the next sweep: {}", document.id(), document.symbol(), e.getMessage());
            } catch (Exception e) {
                log.error("Failed to extract knowledge for document {} (symbol={}): {}",
                    document.id(), document.symbol(), e.getMessage(), e);
                failed++;
            }
        }

        relationshipBuilder.expandCompetitorGroups();

        log.info("Knowledge extraction: {} documents processed, {} skipped as repeats of an already-extracted filing, "
            + "{} deferred to the next sweep, {} failed (of {})", succeeded, repeats, deferred, failed, pending.size());
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

    /** Returns true if the document was skipped as a repeat of one already extracted (no LLM call made). */
    private boolean extractOne(PendingKnowledgeDocument document) {
        String text = document.extractedText();
        String textHash = text == null || text.isBlank() ? null : TextHash.of(text);

        // One exchange filing is often attached to several announcements. Re-extracting the same text
        // costs a full Claude pass per copy and counts the filing's facts once per announcement.
        if (textHash != null) {
            Optional<UUID> original = repeatDetector.findExtractedOriginal(
                document.id(), document.source(), document.instrumentId(), textHash
            );
            if (original.isPresent()) {
                repeatDetector.markRepeat(document.id(), textHash, original.get());
                log.info("Document {} (symbol={}) has the same text as already-extracted document {} - skipped, no LLM call",
                    document.id(), document.symbol(), original.get());
                return true;
            }
        }

        // A results-season day can bring millions of characters of announcements; past the day's
        // allowance the rest wait for the next sweep (oldest first) instead of all being paid for at once.
        if (DocumentSource.EXCHANGE_ANNOUNCEMENT.name().equals(document.source()) && text != null
            && !textBudget.tryCharge(document.source(), text.length())) {
            throw new ExtractionDeferredException(
                "today's announcement text allowance is used up (document is " + text.length() + " characters) - left for the next sweep", null
            );
        }

        // Every LLM call happens first and nothing is written until all of them have succeeded. If a
        // call fails (or defers the document), the document is exactly as it was and the next sweep
        // starts clean - previously Stage 1's rows were written before Stage 2 ran, so a Stage 2
        // failure left a summary row behind and every later sweep died on its duplicate key.
        DocumentClassification classification = classificationEngine.classify(document.extractedText(), document.source());
        DocumentContext context = new DocumentContext(
            document.id(), document.instrumentId(), document.symbol(), document.extractedText(), classification
        );
        List<ExtractedFact> facts = router.route(context);

        writer.writeClassification(document.id(), classification);
        writer.writeFacts(document.id(), facts);
        relationshipBuilder.build(document.id(), document.symbol(), facts);

        jdbcTemplate.update(
            "UPDATE corporate.documents SET status = 'KNOWLEDGE_EXTRACTED', text_hash = ? WHERE id = ?", textHash, document.id()
        );
        return false;
    }
}
