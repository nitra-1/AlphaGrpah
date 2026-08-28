package com.alphagraph.ownership.deals;

import com.alphagraph.common.rules.ArithmeticRuleEvaluator;
import com.alphagraph.common.rules.EvaluationResult;
import com.alphagraph.common.rules.MetricContext;
import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleEvaluator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.common.rules.WeightedAverageRuleEvaluator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Two-pass rule evaluation for one deal, reusing {@code common.rules} wholesale - no new
 * rule-evaluation Java needed. Deliberately not a {@code common.engine.Engine} - that interface's
 * single {@code value()}/{@code confidence()} {@code Score} contract doesn't fit a per-event
 * result that carries two genuinely independent outputs (materiality and reported net flow), not
 * one blended number.
 *
 * <p>Pass 1 ({@link ArithmeticRuleEvaluator}): three sub-scores - {@code dealToAdtvRatio} banded
 * against {@code deal-materiality-adtv-ratio}, and the two direction-neutral repetition/breadth
 * counts (already resolved to this deal's own side by {@link BulkDealContextReader}) banded
 * against {@code deal-materiality-repetition}/{@code deal-materiality-breadth}. Pass 2
 * ({@link WeightedAverageRuleEvaluator}, matching {@code decision.engine.DecisionScoringEngine}'s
 * exact renormalized-weighted-average shape): blends the three sub-scores 70/20/10 into the final
 * 0-100 {@code materialityScore}.
 *
 * <p>{@code reportedFlowState} is a genuinely separate signal, never blended into
 * {@code materialityScore} - bulk/block deals are disclosed, qualifying participants only;
 * "reported buy value > reported sell value" proves only that the visible deals lean buy-side,
 * not genuine accumulation. Named "reported", not "accumulation", for exactly that reason. A
 * VERY_HIGH-materiality SELL deal is never confused with a VERY_HIGH-materiality BUY deal because
 * {@code direction} is stored plainly alongside {@code materialityLevel}.
 */
@Component
class DealMaterialityEngine {

    private static final RuleEvaluator ARITHMETIC = new ArithmeticRuleEvaluator();
    private static final RuleEvaluator WEIGHTED_AVERAGE = new WeightedAverageRuleEvaluator();
    private static final String BLEND_PREFIX = "deal-materiality-blend-";

    DealMaterialityResult calculate(DealMaterialityInput input, RuleSet rules) {
        BigDecimal dealToAdtvRatio = input.dealValue().divide(input.adtv20(), 4, RoundingMode.HALF_UP);

        Map<String, Double> pass1Metrics = Map.of(
            "dealToAdtvRatio", dealToAdtvRatio.doubleValue(),
            "sameSideClientDealCount20CalendarDays", (double) input.sameSideClientDealCount20CalendarDays(),
            "distinctSameSideClients20CalendarDays", (double) input.distinctSameSideClients20CalendarDays()
        );
        MetricContext pass1Context = new MetricContext(pass1Metrics);

        double adtvRatioScore = evaluateExact(rules, "deal-materiality-adtv-ratio", pass1Context);
        double repetitionScore = evaluateExact(rules, "deal-materiality-repetition", pass1Context);
        double breadthScore = evaluateExact(rules, "deal-materiality-breadth", pass1Context);

        Map<String, Double> pass2Metrics = new HashMap<>();
        pass2Metrics.put("materialityAdtvRatioScore", adtvRatioScore);
        pass2Metrics.put("materialityRepetitionScore", repetitionScore);
        pass2Metrics.put("materialityBreadthScore", breadthScore);
        MetricContext pass2Context = new MetricContext(pass2Metrics);

        double materialityScore = weightedAverage(rules, pass2Context);
        String materialityLevel = materialityLevelFor(materialityScore);

        BigDecimal reportedNetFlowValue = input.reportedBuyValue20CalendarDays().subtract(input.reportedSellValue20CalendarDays());
        BigDecimal reportedTotalValue = input.reportedBuyValue20CalendarDays().add(input.reportedSellValue20CalendarDays());
        double reportedNetFlowRatio = reportedTotalValue.signum() == 0
            ? 0.0
            : reportedNetFlowValue.divide(reportedTotalValue, 6, RoundingMode.HALF_UP).doubleValue();
        String reportedFlowState = reportedFlowStateFor(reportedNetFlowRatio);

        return new DealMaterialityResult(
            input.discoveredDealId(), input.symbol(), input.dealDate(), input.dealValue(),
            input.adtv20(), dealToAdtvRatio, input.direction(),
            input.sameSideClientDealCount20CalendarDays(), input.distinctSameSideClients20CalendarDays(),
            input.distinctBuyers20CalendarDays(), input.distinctSellers20CalendarDays(),
            materialityScore, materialityLevel,
            input.reportedBuyValue20CalendarDays(), input.reportedSellValue20CalendarDays(),
            reportedNetFlowValue, reportedNetFlowRatio, reportedFlowState,
            rules.version(), Instant.now()
        );
    }

    /** Sums the one matching band's weight - the DB seed's bands are a non-overlapping partition of the metric's real range (see V14's migration comment), so exactly one condition ever matches. */
    private static double evaluateExact(RuleSet rules, String ruleName, MetricContext context) {
        Optional<Rule> rule = rules.rules().stream().filter(r -> r.name().equals(ruleName)).findFirst();
        if (rule.isEmpty()) {
            return 0.0;
        }
        EvaluationResult result = ARITHMETIC.evaluate(rule.get(), context);
        return result.metricPresent() ? result.contribution() : 0.0;
    }

    /** Renormalized weighted average over every matched {@code deal-materiality-blend-*} rule - identical shape to DecisionScoringEngine#weightedAverage, so a sub-score that's somehow absent shrinks the denominator rather than silently dragging the average toward zero. */
    private static double weightedAverage(RuleSet rules, MetricContext context) {
        double totalContribution = 0.0;
        double totalWeight = 0.0;
        for (Rule rule : rules.rules()) {
            if (!rule.name().startsWith(BLEND_PREFIX)) {
                continue;
            }
            EvaluationResult result = WEIGHTED_AVERAGE.evaluate(rule, context);
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
        double average = clamp(totalContribution / totalWeight, 0.0, 100.0);
        return Math.round(average * 100.0) / 100.0;
    }

    /**
     * First-pass calibration, a plain hardcoded Java ladder on the final blended score - matching
     * {@code corporate.signal.CorporateSignalEngine#ratingFor}'s established convention (final
     * categorical banding is never itself a DB rule). Not claimed as an authoritative industry
     * cutoff, same disclaimer every other engine's rule seed already carries.
     */
    private static String materialityLevelFor(double score) {
        if (score >= 80) {
            return "VERY_HIGH";
        } else if (score >= 60) {
            return "HIGH";
        } else if (score >= 35) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /** Five-state ladder on reportedNetFlowRatio, matching InstitutionalEngine#classifyBehaviour's convention. */
    private static String reportedFlowStateFor(double ratio) {
        if (ratio > 0.25) {
            return "STRONG_NET_BUYING";
        } else if (ratio > 0.10) {
            return "NET_BUYING";
        } else if (ratio >= -0.10) {
            return "BALANCED";
        } else if (ratio >= -0.25) {
            return "NET_SELLING";
        }
        return "STRONG_NET_SELLING";
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
