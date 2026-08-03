package com.alphagraph.corporate.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Persists one document's {@link CanonicalExtraction} across the three canonical tables
 * (document_facts, document_topics, document_summary). No upsert key on facts/topics - a document
 * only ever goes through canonical extraction once, per {@link KnowledgeExtractionOrchestrator}'s
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

    void write(UUID documentId, CanonicalExtraction extraction) {
        for (ExtractedFact fact : extraction.facts()) {
            jdbcTemplate.update(
                "INSERT INTO corporate.document_facts (id, document_id, fact_type, fact_value, unit, confidence) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), documentId, fact.factType(), fact.value(), fact.unit(), fact.confidence()
            );
        }

        for (String topic : extraction.topics()) {
            jdbcTemplate.update(
                "INSERT INTO corporate.document_topics (id, document_id, topic) VALUES (?, ?, ?)",
                UUID.randomUUID(), documentId, topic
            );
        }

        jdbcTemplate.update(
            "INSERT INTO corporate.document_summary (document_id, document_type, sentiment, confidence, summary, extracted_at) VALUES (?, ?, ?, ?, ?, ?)",
            documentId, extraction.documentType(), extraction.sentiment().name(), extraction.confidence(),
            extraction.summary(), Timestamp.from(Instant.now())
        );
    }
}
