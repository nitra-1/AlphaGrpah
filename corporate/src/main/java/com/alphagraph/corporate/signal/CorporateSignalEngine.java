package com.alphagraph.corporate.signal;

import com.alphagraph.common.engine.Engine;
import com.alphagraph.common.rules.ArithmeticRuleEvaluator;
import com.alphagraph.common.rules.EvaluationResult;
import com.alphagraph.common.rules.MetricContext;
import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleEvaluator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.corporate.api.CorporateRating;
import com.alphagraph.corporate.api.CorporateScore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Combines one instrument's Order Book/Management Commentary/News Catalyst/Corporate Event
 * signals into a single {@link CorporateScore} - the roadmap's own worked example ("Order Win +
 * Positive Guidance + Capacity Expansion + Strong Demand = Corporate Score") names one ingredient
 * from each domain. Same {@code common.engine.Engine} + {@code common.rules.RuleSet} shape as
 * {@code corporate.news.NewsCatalystEngine} and every other Score-implementing engine in this
 * project - a genuine weighted composite, not a classifier.
 */
@Component
class CorporateSignalEngine implements Engine<CorporateSignalInput, CorporateScore> {

    private final RuleEvaluator ruleEvaluator = new ArithmeticRuleEvaluator();

    @Override
    public CorporateScore calculate(CorporateSignalInput input, RuleSet rules) {
        Map<String, Double> metrics = new HashMap<>();
        if (input.orderBookScore() != null) {
            metrics.put("corporateOrderBookScore", input.orderBookScore());
        }
        if (input.managementScore() != null) {
            metrics.put("corporateManagementScore", input.managementScore());
        }
        if (input.newsCatalystScore() != null) {
            metrics.put("corporateNewsCatalystScore", input.newsCatalystScore());
        }
        metrics.put("corporateEventNetSignal", (double) input.netEventSignal());

        MetricContext context = new MetricContext(metrics);
        double rawScore = 0.0;
        for (Rule rule : rules.rules()) {
            EvaluationResult result = ruleEvaluator.evaluate(rule, context);
            rawScore += result.contribution();
        }
        double corporateScore = clamp(50.0 + rawScore * 10.0, 0.0, 100.0);

        return new CorporateScore(
            input.instrumentId(), input.symbol(), input.asOfDate(),
            corporateScore, ratingFor(corporateScore),
            input.orderBookScore(), input.managementScore(), input.newsCatalystScore(), input.netEventSignal(),
            confidence(input), rules.version(), Instant.now()
        );
    }

    private static CorporateRating ratingFor(double score) {
        if (score >= 80) {
            return CorporateRating.EXCELLENT;
        } else if (score >= 65) {
            return CorporateRating.STRONG;
        } else if (score >= 40) {
            return CorporateRating.NEUTRAL;
        } else if (score >= 25) {
            return CorporateRating.WEAK;
        }
        return CorporateRating.POOR;
    }

    /** 40 baseline + 15 per domain with real evidence, so a score backed by all four domains (100) reads as far more trustworthy than one resting on a single domain (55). */
    private static double confidence(CorporateSignalInput input) {
        int domainsPresent = 0;
        domainsPresent += input.orderBookScore() != null ? 1 : 0;
        domainsPresent += input.managementScore() != null ? 1 : 0;
        domainsPresent += input.newsCatalystScore() != null ? 1 : 0;
        domainsPresent += input.totalEventCount() > 0 ? 1 : 0;
        return 40.0 + 15.0 * domainsPresent;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
