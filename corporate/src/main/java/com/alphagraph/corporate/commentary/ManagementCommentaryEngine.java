package com.alphagraph.corporate.commentary;

import com.alphagraph.common.engine.Engine;
import com.alphagraph.common.rules.ArithmeticRuleEvaluator;
import com.alphagraph.common.rules.EvaluationResult;
import com.alphagraph.common.rules.MetricContext;
import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleEvaluator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.corporate.api.CommitmentLevel;
import com.alphagraph.corporate.api.GuidanceDirection;
import com.alphagraph.corporate.api.GuidanceTrend;
import com.alphagraph.corporate.api.ManagementCommentarySnapshot;
import com.alphagraph.corporate.api.ManagementCredibility;
import com.alphagraph.corporate.api.ManagementObservation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates one instrument's management-observation history (Layer 1) into a point-in-time
 * {@link ManagementCommentarySnapshot} (Layer 2) - a genuine {@code common.engine.Engine}
 * implementation, exactly like {@code corporate.orderbook.OrderBookAggregationEngine} and unlike
 * Module 2.3's topic-matching classifier.
 *
 * <p><b>Trend and persistence are computed only from REVENUE_GUIDANCE observations</b> - the
 * roadmap's own worked example tracks revenue guidance specifically ("Growth Visibility" IS
 * visibility into future revenue growth), and it's the metric type most reliably stated as a
 * clean number; Demand/Pricing/Competition/Risk commentary is usually qualitative prose with
 * nothing to trend against.
 *
 * <p><b>Management Credibility is a real, disclosed simplification</b>: a proxy from the
 * consistency of direction and commitment level across observations over time, NOT from
 * comparing past guidance against actual reported results - no infrastructure exists yet to
 * cross-reference this engine's observations against Fundamental Engine's real revenue figures
 * (that would be a future intelligence-bridging engine, matching Module 1.9's Risk Engine
 * precedent for combining domains).
 */
@Component
class ManagementCommentaryEngine implements Engine<ManagementCommentaryInput, ManagementCommentarySnapshot> {

    private static final String REVENUE_GUIDANCE = "REVENUE_GUIDANCE";

    private final RuleEvaluator ruleEvaluator = new ArithmeticRuleEvaluator();

    @Override
    public ManagementCommentarySnapshot calculate(ManagementCommentaryInput input, RuleSet rules) {
        List<ManagementObservation> revenueGuidance = input.observations().stream()
            .filter(o -> REVENUE_GUIDANCE.equals(o.metricType()))
            .toList();

        Map<String, Double> metrics = new HashMap<>();
        if (!revenueGuidance.isEmpty()) {
            metrics.put("guidanceDirectionSignal", directionSignal(revenueGuidance.get(0).direction()));
            metrics.put("guidancePersistenceQuarters", (double) persistenceQuarters(revenueGuidance));
            metrics.put("commitmentStrengthSignal", commitmentStrength(revenueGuidance.get(0).commitmentLevel()));
        }

        MetricContext context = new MetricContext(metrics);
        double rawScore = 0.0;
        for (Rule rule : rules.rules()) {
            EvaluationResult result = ruleEvaluator.evaluate(rule, context);
            rawScore += result.contribution();
        }
        double growthVisibilityScore = clamp(50.0 + rawScore * 10.0, 0.0, 100.0);

        GuidanceTrend trend = computeTrend(revenueGuidance);
        ManagementCredibility credibility = computeCredibility(metrics);
        double confidence = confidence(revenueGuidance);

        return new ManagementCommentarySnapshot(
            input.instrumentId(), input.symbol(), input.asOfDate(),
            growthVisibilityScore, trend, credibility, confidence, rules.version(), Instant.now()
        );
    }

    private static double directionSignal(GuidanceDirection direction) {
        return switch (direction) {
            case POSITIVE -> 1.0;
            case NEGATIVE -> -1.0;
            case NEUTRAL -> 0.0;
        };
    }

    private static double commitmentStrength(CommitmentLevel level) {
        return switch (level) {
            case LOW -> 0.0;
            case MEDIUM -> 1.0;
            case HIGH -> 2.0;
            case VERY_HIGH -> 3.0;
        };
    }

    /** Consecutive POSITIVE-direction observations counting from the most recent, stopping at the first non-positive. */
    private static int persistenceQuarters(List<ManagementObservation> revenueGuidanceNewestFirst) {
        int count = 0;
        for (ManagementObservation observation : revenueGuidanceNewestFirst) {
            if (observation.direction() != GuidanceDirection.POSITIVE) {
                break;
            }
            count++;
        }
        return count;
    }

    /** UNKNOWN (not fabricated as STABLE) when fewer than 2 numeric revenue-guidance observations exist to compare. */
    private static GuidanceTrend computeTrend(List<ManagementObservation> revenueGuidanceNewestFirst) {
        List<ManagementObservation> numeric = revenueGuidanceNewestFirst.stream()
            .filter(o -> o.guidanceValueNumeric() != null)
            .toList();
        if (numeric.size() < 2) {
            return GuidanceTrend.UNKNOWN;
        }
        double latest = numeric.get(0).guidanceValueNumeric();
        double previous = numeric.get(1).guidanceValueNumeric();
        if (latest > previous) {
            return GuidanceTrend.UPGRADING;
        }
        if (latest < previous) {
            return GuidanceTrend.DOWNGRADING;
        }
        return GuidanceTrend.STABLE;
    }

    private static ManagementCredibility computeCredibility(Map<String, Double> metrics) {
        Double persistence = metrics.get("guidancePersistenceQuarters");
        Double commitment = metrics.get("commitmentStrengthSignal");
        if (persistence == null || commitment == null) {
            return ManagementCredibility.MEDIUM;
        }
        if (persistence >= 3 && commitment >= 2) {
            return ManagementCredibility.HIGH;
        }
        if (persistence == 0 || commitment == 0) {
            return ManagementCredibility.LOW;
        }
        return ManagementCredibility.MEDIUM;
    }

    private static double confidence(List<ManagementObservation> revenueGuidance) {
        if (revenueGuidance.isEmpty()) {
            return 50.0;
        }
        return 50.0 + 50.0 * (Math.min(revenueGuidance.size(), 5) / 5.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
