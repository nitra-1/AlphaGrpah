package com.alphagraph.corporate.events;

import com.alphagraph.corporate.api.Sentiment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One document's identity plus its canonical knowledge - the input to {@link CorporateEventEngine}. */
record KnowledgeDocument(UUID id, UUID instrumentId, String symbol, KnowledgeContext knowledge) {
}

/**
 * Reads documents that have reached KNOWLEDGE_EXTRACTED (Module 2.2's canonical extraction) but
 * haven't yet been checkpointed for this engine ({@code CONSUMER}), assembling each one's topics,
 * facts, and summary into a {@link KnowledgeContext}. Unlike the pre-retrofit
 * {@code ProcessedDocumentReader}, idempotency here comes from
 * {@code corporate.document_consumer_checkpoints}, not document status - the same
 * KNOWLEDGE_EXTRACTED document is also read independently by the Order Book Engine and future
 * consumers, so document status can no longer mean "has this specific engine looked at it yet".
 */
@Component
class KnowledgeDocumentReader {

    static final String CONSUMER = "CORPORATE_EVENT_ENGINE";

    private final JdbcTemplate jdbcTemplate;

    KnowledgeDocumentReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<KnowledgeDocument> findUnprocessed() {
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
            """
            SELECT d.id, d.instrument_id, d.symbol
            FROM corporate.documents d
            WHERE d.status = 'KNOWLEDGE_EXTRACTED'
              AND NOT EXISTS (
                  SELECT 1 FROM corporate.document_consumer_checkpoints c
                  WHERE c.document_id = d.id AND c.consumer = ?
              )
            """,
            CONSUMER
        );

        return candidates.stream()
            .map(row -> {
                UUID documentId = (UUID) row.get("id");
                return new KnowledgeDocument(
                    documentId, (UUID) row.get("instrument_id"), (String) row.get("symbol"),
                    readKnowledge(documentId)
                );
            })
            .toList();
    }

    private KnowledgeContext readKnowledge(UUID documentId) {
        Map<String, Object> summaryRow = jdbcTemplate.queryForMap(
            "SELECT document_type, sentiment, confidence, summary FROM corporate.document_summary WHERE document_id = ?",
            documentId
        );

        List<String> topics = jdbcTemplate.queryForList(
            "SELECT topic FROM corporate.document_topics WHERE document_id = ?", String.class, documentId
        );

        Map<String, FactValue> facts = new HashMap<>();
        for (Map<String, Object> factRow : jdbcTemplate.queryForList(
            "SELECT fact_type, fact_value, unit FROM corporate.document_facts WHERE document_id = ?", documentId
        )) {
            facts.put((String) factRow.get("fact_type"), new FactValue((String) factRow.get("fact_value"), (String) factRow.get("unit")));
        }

        return new KnowledgeContext(
            topics, facts, (String) summaryRow.get("document_type"), (String) summaryRow.get("summary"),
            Sentiment.valueOf((String) summaryRow.get("sentiment")),
            ((Number) summaryRow.get("confidence")).doubleValue()
        );
    }
}
