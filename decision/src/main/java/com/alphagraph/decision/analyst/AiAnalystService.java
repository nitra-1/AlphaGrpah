package com.alphagraph.decision.analyst;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * The AI Analyst's public entry point. Module 2.9 scoped this narrowly to one capability - "why
 * has this instrument's Corporate Score changed" - not open-ended natural-language query routing
 * (intent classification, entity extraction from free text), a materially bigger, separate task
 * left for later if genuinely needed. Module 3.7 adds the second capability the roadmap's own
 * worked example names directly ("Why moved from Rank 18 to Rank 5?"), now that Module 3.1 gives
 * a real Rank to explain - both share the same {@link AiAnalystClient}, since its prompt was
 * already generic ("why has this instrument's outlook changed"), not hardcoded to Corporate Score.
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

    /** "Explain why {symbol}'s Corporate Score has changed" - every fact behind the answer was computed deterministically before the LLM ever ran. */
    public String explainScoreChange(UUID instrumentId) {
        String symbol = resolveSymbol(instrumentId);
        List<EvidenceFact> facts = evidenceBuilder.buildScoreEvidence(instrumentId);
        return client.explain(symbol, facts);
    }

    /** "Explain why {symbol}'s Swing Rank has changed" (Module 3.7) - same deterministic-facts-only discipline. */
    public String explainRankChange(UUID instrumentId) {
        String symbol = resolveSymbol(instrumentId);
        List<EvidenceFact> facts = evidenceBuilder.buildRankEvidence(instrumentId);
        return client.explain(symbol, facts);
    }

    private String resolveSymbol(UUID instrumentId) {
        return jdbcTemplate.queryForObject("SELECT symbol FROM reference.instruments WHERE id = ?", String.class, instrumentId);
    }
}
