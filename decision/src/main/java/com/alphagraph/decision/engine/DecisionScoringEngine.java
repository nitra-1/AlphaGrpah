package com.alphagraph.decision.engine;

import com.alphagraph.common.engine.Engine;
import com.alphagraph.common.rules.EvaluationResult;
import com.alphagraph.common.rules.MetricContext;
import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleEvaluator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.common.rules.WeightedAverageRuleEvaluator;
import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.decision.api.DecisionScore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Blends one instrument's six domain scores into Swing Score and Long-Term Score - the roadmap's
 * originally-planned Phase 1 module (Swing Score, Long-Term Score, Rank), built here once all six
 * real inputs existed. A single {@code calculate} call takes the combined {@code decision-swing-*}
 * + {@code decision-longterm-*} {@link RuleSet} ({@link DecisionRuleSetLoader}) and partitions it
 * by name prefix internally, so the {@code common.engine.Engine} contract still holds (one input,
 * one {@link RuleSet}, one {@link com.alphagraph.common.engine.Score}) even though two horizons
 * are computed. Each horizon is a genuine weighted average via {@link WeightedAverageRuleEvaluator},
 * renormalized over whichever domains are actually present - a missing domain simply doesn't
 * shrink the achievable ceiling, it's excluded from both numerator and denominator, so sparse data
 * is penalized once (via {@link #confidence}) rather than twice.
 *
 * <p>{@code swingRank}/{@code longTermRank} are always null here - ranking is a cross-instrument
 * concern only {@code DecisionScoringOrchestrator}'s second pass can resolve.
 */
@Component
class DecisionScoringEngine implements Engine<DecisionScoringInput, DecisionScore> {

    private static final String SWING_PREFIX = "decision-swing-";
    private static final String LONGTERM_PREFIX = "decision-longterm-";

    private final RuleEvaluator ruleEvaluator = new WeightedAverageRuleEvaluator();

    @Override
    public DecisionScore calculate(DecisionScoringInput input, RuleSet rules) {
        Map<String, Double> metrics = new HashMap<>();
        putIfPresent(metrics, "decisionTechnicalScore", input.technicalScore());
        putIfPresent(metrics, "decisionFundamentalScore", input.fundamentalScore());
        putIfPresent(metrics, "decisionInstitutionalScore", input.institutionalScore());
        putIfPresent(metrics, "decisionSectorScore", input.sectorScore());
        putIfPresent(metrics, "decisionRiskScore", input.riskScore());
        putIfPresent(metrics, "decisionCorporateScore", input.corporateScore());
        MetricContext context = new MetricContext(metrics);

        double swingScore = weightedAverage(rules, context, SWING_PREFIX);
        double longTermScore = weightedAverage(rules, context, LONGTERM_PREFIX);
        double confidence = confidence(input);

        return new DecisionScore(
            input.instrumentId(), input.symbol(), input.asOfDate(),
            swingScore, ratingFor(swingScore), null,
            longTermScore, ratingFor(longTermScore), null,
            input.technicalScore(), input.fundamentalScore(), input.institutionalScore(),
            input.sectorScore(), input.riskScore(), input.corporateScore(),
            confidence, rules.version(), Instant.now()
        );
    }

    private double weightedAverage(RuleSet rules, MetricContext context, String prefix) {
        double totalContribution = 0.0;
        double totalWeight = 0.0;
        for (Rule rule : rules.rules()) {
            if (!rule.name().startsWith(prefix)) {
                continue;
            }
            EvaluationResult result = ruleEvaluator.evaluate(rule, context);
            if (!result.metricPresent()) {
                continue;
            }
            totalContribution += result.contribution();
            for (RuleCondition matched : result.matchedConditions()) {
                totalWeight += matched.weight();
            }
        }
        if (totalWeight == 0.0) {
            return 0.0;
        }
        // Rounded to 2dp - decision_scores stores numeric(5,2), and binary floating point
        // representation of weights like 0.35/0.15 can otherwise leave sub-cent noise
        // (e.g. 60.00000000000001) that has no business surviving into a persisted score.
        double average = clamp(totalContribution / totalWeight, 0.0, 100.0);
        return Math.round(average * 100.0) / 100.0;
    }

    /** 10 baseline + 15 per domain present (of 6), so full six-domain coverage reads as fully trustworthy (100) and a single-domain score reads as barely trustworthy (25). */
    private static double confidence(DecisionScoringInput input) {
        int domainsPresent = 0;
        domainsPresent += input.technicalScore() != null ? 1 : 0;
        domainsPresent += input.fundamentalScore() != null ? 1 : 0;
        domainsPresent += input.institutionalScore() != null ? 1 : 0;
        domainsPresent += input.sectorScore() != null ? 1 : 0;
        domainsPresent += input.riskScore() != null ? 1 : 0;
        domainsPresent += input.corporateScore() != null ? 1 : 0;
        return 10.0 + 15.0 * domainsPresent;
    }

    private static DecisionRating ratingFor(double score) {
        if (score >= 80) {
            return DecisionRating.STRONG_BUY;
        } else if (score >= 65) {
            return DecisionRating.BUY;
        } else if (score >= 40) {
            return DecisionRating.HOLD;
        } else if (score >= 25) {
            return DecisionRating.REDUCE;
        }
        return DecisionRating.AVOID;
    }

    private static void putIfPresent(Map<String, Double> metrics, String key, Double value) {
        if (value != null) {
            metrics.put(key, value);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
