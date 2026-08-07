package com.alphagraph.corporate.newsfeed;

import com.alphagraph.corporate.knowledge.KnowledgeExtractionOrchestrator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** The admin's two decisions on a PENDING_REVIEW article - see {@link NewsRelevanceFilter} for why one exists at all. */
@Component
public class NewsReviewService {

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeExtractionOrchestrator orchestrator;

    public NewsReviewService(JdbcTemplate jdbcTemplate, KnowledgeExtractionOrchestrator orchestrator) {
        this.jdbcTemplate = jdbcTemplate;
        this.orchestrator = orchestrator;
    }

    /**
     * Promotes an article for real (billed) Claude extraction, right now - not queued for the next
     * scheduled run. The status flip is the atomic guard against a double "keep" (e.g. a duplicate
     * click); {@link KnowledgeExtractionOrchestrator#extractDocument} does the actual work and
     * leaves the row at KNOWLEDGE_EXTRACTED when it finishes.
     */
    public boolean keep(UUID documentId) {
        int updated = jdbcTemplate.update(
            "UPDATE corporate.documents SET status = 'PROCESSED' WHERE id = ? AND status = 'PENDING_REVIEW'", documentId
        );
        if (updated == 0) {
            return false;
        }
        orchestrator.extractDocument(documentId);
        return true;
    }

    /** Terminal rejection - the article is never extracted. */
    public boolean discard(UUID documentId) {
        int updated = jdbcTemplate.update(
            "UPDATE corporate.documents SET status = 'DISCARDED' WHERE id = ? AND status = 'PENDING_REVIEW'", documentId
        );
        return updated > 0;
    }
}
