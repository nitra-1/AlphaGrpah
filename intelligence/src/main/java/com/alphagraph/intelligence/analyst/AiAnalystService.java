package com.alphagraph.intelligence.analyst;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Module 2.9: the AI Analyst's public entry point. Scoped narrowly to the one capability the
 * user's spec actually demonstrates - "explain why this instrument's outlook has changed" - not
 * open-ended natural-language query routing (intent classification, entity extraction from free
 * text), which is a materially bigger, separate task left for a later module if genuinely needed.
 */
@Component
public class AiAnalystService {

    private final JdbcTemplate jdbcTemplate;
    private final AnalystEvidenceBuilder evidenceBuilder;
    private final AiAnalystClient client;

    public AiAnalystService(JdbcTemplate jdbcTemplate, AnalystEvidenceBuilder evidenceBuilder, AiAnalystClient client) {
        this.jdbcTemplate = jdbcTemplate;
        this.evidenceBuilder = evidenceBuilder;
        this.client = client;
    }

    /** "Explain why {symbol}'s outlook has changed" - every fact behind the answer was computed deterministically before the LLM ever ran. */
    public String explainScoreChange(UUID instrumentId) {
        String symbol = jdbcTemplate.queryForObject("SELECT symbol FROM reference.instruments WHERE id = ?", String.class, instrumentId);
        List<EvidenceFact> facts = evidenceBuilder.buildEvidence(instrumentId);
        return client.explain(symbol, facts);
    }
}
