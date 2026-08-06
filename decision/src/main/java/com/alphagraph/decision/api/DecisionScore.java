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
 */
public record DecisionScore(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    double swingScore, DecisionRating swingRating, Integer swingRank,
    double longTermScore, DecisionRating longTermRating, Integer longTermRank,
    Double technicalScore, Double fundamentalScore, Double institutionalScore,
    Double sectorScore, Double riskScore, Double corporateScore,
    double confidence, int ruleSetVersion, Instant computedAt
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
