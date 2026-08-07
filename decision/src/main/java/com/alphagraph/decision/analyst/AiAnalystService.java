package com.alphagraph.decision.analyst;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The AI Analyst's public entry point. Module 2.9 scoped this narrowly to one capability - "why
 * has this instrument's Corporate Score changed" - not open-ended natural-language query routing
 * (intent classification, entity extraction from free text), a materially bigger, separate task
 * left for later if genuinely needed. Module 3.7 adds the second capability the roadmap's own
 * worked example names directly ("Why moved from Rank 18 to Rank 5?"), now that Module 3.1 gives
 * a real Rank to explain - both share the same {@link AiAnalystClient}, since its prompt was
 * already generic ("why has this instrument's outlook changed"), not hardcoded to Corporate Score.
 *
 * <p>Caching retrofit: both capabilities were originally a real, uncached Claude call on every
 * request - deliberate at the time since the endpoint was expected to be low-traffic, but that
 * doesn't hold once many users can ask the same question about the same instrument on the same
 * day. {@link AnalystExplanationReader}/{@link AnalystExplanationStore} cache one explanation per
 * (instrument, type, calendar day) - the underlying facts only change on a scheduled recompute (at
 * most once daily), so a cache hit is never stale within the same day. The evidence builder and
 * Claude are only invoked on a genuine miss.
 */
@Component
public class AiAnalystService {

    static final String SCORE_CHANGE = "SCORE_CHANGE";
    static final String RANK_CHANGE = "RANK_CHANGE";

    private final JdbcTemplate jdbcTemplate;
    private final AnalystEvidenceBuilder evidenceBuilder;
    private final AiAnalystClient client;
    private final AnalystExplanationReader explanationReader;
    private final AnalystExplanationStore explanationStore;

    public AiAnalystService(
        JdbcTemplate jdbcTemplate, AnalystEvidenceBuilder evidenceBuilder, AiAnalystClient client,
        AnalystExplanationReader explanationReader, AnalystExplanationStore explanationStore
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.evidenceBuilder = evidenceBuilder;
        this.client = client;
        this.explanationReader = explanationReader;
        this.explanationStore = explanationStore;
    }

    /** "Explain why {symbol}'s Corporate Score has changed" - every fact behind the answer was computed deterministically before the LLM ever ran. */
    public String explainScoreChange(UUID instrumentId) {
        return explainCached(instrumentId, SCORE_CHANGE, () -> evidenceBuilder.buildScoreEvidence(instrumentId));
    }

    /** "Explain why {symbol}'s Swing Rank has changed" (Module 3.7) - same deterministic-facts-only discipline. */
    public String explainRankChange(UUID instrumentId) {
        return explainCached(instrumentId, RANK_CHANGE, () -> evidenceBuilder.buildRankEvidence(instrumentId));
    }

    private String explainCached(UUID instrumentId, String explanationType, Supplier<List<EvidenceFact>> factsSupplier) {
        LocalDate today = LocalDate.now();
        Optional<String> cached = explanationReader.find(instrumentId, explanationType, today);
        if (cached.isPresent()) {
            return cached.get();
        }

        String symbol = resolveSymbol(instrumentId);
        String explanation = client.explain(symbol, factsSupplier.get());
        explanationStore.write(instrumentId, explanationType, today, explanation);
        return explanation;
    }

    private String resolveSymbol(UUID instrumentId) {
        return jdbcTemplate.queryForObject("SELECT symbol FROM reference.instruments WHERE id = ?", String.class, instrumentId);
    }
}
