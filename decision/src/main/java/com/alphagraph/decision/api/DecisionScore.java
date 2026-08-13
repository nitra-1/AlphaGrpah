package com.alphagraph.decision.api;

import com.alphagraph.common.engine.Score;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The Scoring/Decision Engine's output for one instrument on one date, mirroring
 * {@code decision.decision_scores} - the roadmap's originally-planned Phase 1 module, built here
 * in Phase 3 once its six real inputs (Technical, Fundamental, Institutional, Sector, Risk,
 * Corporate) all existed. {@code value()} is {@code swingScore} - the more frequently-cited
 * number in the roadmap's own "Why moved from Rank 18 to Rank 5?" worked example - but
 * {@code longTermScore} and its own rank are carried in full, not derived from swingScore.
 * {@code swingRank}/{@code longTermRank} are nullable: null until
 * {@code decision.engine.DecisionScoringOrchestrator}'s ranking pass runs after every instrument
 * for the day has a score (a single-instrument {@code common.engine.Engine} cannot know its own
 * rank). The six sub-scores are nullable and carried alongside the composites for the same reason
 * {@code corporate.api.CorporateScore} carries its own inputs - so a consumer can cite which
 * domain drove a change, not just the final number.
 *
 * <p>Learning Readiness Hardening: the trailing block of fields (from {@code technicalScoreAsOfDate}
 * onward) preserves per-domain provenance - each domain score's own {@code as_of_date},
 * {@code rule_set_version}, and {@code computed_at}, since {@code DecisionScoringOrchestrator}
 * resolves each one independently ("latest valid as of the decision date"), not necessarily all
 * from the same day or the same rule-set version as each other. Appended at the end rather than
 * interleaved so every pre-existing accessor (e.g. {@code .technicalScore()}) and construction
 * site elsewhere in the codebase is unaffected by field position. {@code decisionRunId} ties this
 * row to the {@code decision.decision_runs} batch that produced it; the two
 * {@code *RankUniverseSize} fields record how many instruments were ranked that day, so a rank
 * like "#4" can be read against its actual universe size rather than assumed.
 */
public record DecisionScore(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    double swingScore, DecisionRating swingRating, Integer swingRank,
    double longTermScore, DecisionRating longTermRating, Integer longTermRank,
    Double technicalScore, Double fundamentalScore, Double institutionalScore,
    Double sectorScore, Double riskScore, Double corporateScore,
    double confidence, int ruleSetVersion, Instant computedAt,
    LocalDate technicalScoreAsOfDate, Integer technicalRuleSetVersion, Instant technicalComputedAt,
    LocalDate fundamentalScoreAsOfDate, Integer fundamentalRuleSetVersion, Instant fundamentalComputedAt,
    LocalDate institutionalScoreAsOfDate, Integer institutionalRuleSetVersion, Instant institutionalComputedAt,
    LocalDate sectorScoreAsOfDate, Integer sectorRuleSetVersion, Instant sectorComputedAt,
    LocalDate riskScoreAsOfDate, Integer riskRuleSetVersion, Instant riskComputedAt,
    LocalDate corporateScoreAsOfDate, Integer corporateRuleSetVersion, Instant corporateComputedAt,
    UUID decisionRunId, Integer swingRankUniverseSize, Integer longTermRankUniverseSize
) implements Score {

    @Override
    public double value() {
        return swingScore;
    }

    @Override
    public double confidence() {
        return confidence;
    }
}
