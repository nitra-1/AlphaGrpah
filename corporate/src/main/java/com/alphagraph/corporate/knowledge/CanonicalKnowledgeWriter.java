package com.alphagraph.corporate.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persists canonical output across the three tables Stage 1 and Stage 2 each own a share of:
 * {@link #writeClassification} stores Stage 1's document_topics/document_summary,
 * {@link #writeFacts} stores whatever Stage 2 extractors (via {@link DocumentRouter}) found in
 * document_facts - the Stage 3 normalization step. No upsert key on facts/topics - a document
 * only ever goes through this pipeline once, per {@link KnowledgeExtractionOrchestrator}'s
 * PROCESSED -> KNOWLEDGE_EXTRACTED status guard; document_summary is genuinely 1:1 (PK on
 * document_id), so a re-run would need an explicit upsert, which isn't wired since nothing
 * currently re-runs a KNOWLEDGE_EXTRACTED document.
 */
@Component
class CanonicalKnowledgeWriter {

    private final JdbcTemplate jdbcTemplate;

    CanonicalKnowledgeWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void writeClassification(UUID documentId, DocumentClassification classification) {
        for (String topic : classification.topics()) {
            jdbcTemplate.update(
                "INSERT INTO corporate.document_topics (id, document_id, topic) VALUES (?, ?, ?)",
                UUID.randomUUID(), documentId, topic
            );
        }

        jdbcTemplate.update(
            "INSERT INTO corporate.document_summary (document_id, document_type, sentiment, confidence, summary, extracted_at) VALUES (?, ?, ?, ?, ?, ?)",
            documentId, classification.documentType(), classification.sentiment().name(), classification.confidence(),
            classification.summary(), Timestamp.from(Instant.now())
        );
    }

    void writeFacts(UUID documentId, List<ExtractedFact> facts) {
        for (ExtractedFact fact : facts) {
            jdbcTemplate.update(
                "INSERT INTO corporate.document_facts (id, document_id, fact_type, fact_value, unit, confidence, commitment_level, fact_group) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), documentId, fact.factType(), fact.value(), fact.unit(), fact.extractionConfidence(), fact.commitmentLevel(), fact.factGroup()
            );
        }
    }
}
