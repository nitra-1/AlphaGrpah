package com.alphagraph.risk.engine;

import com.alphagraph.common.engine.Engine;
import com.alphagraph.common.rules.ArithmeticRuleEvaluator;
import com.alphagraph.common.rules.EvaluationResult;
import com.alphagraph.common.rules.MetricContext;
import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleEvaluator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.risk.api.RiskEngineInput;
import com.alphagraph.risk.api.RiskLevel;
import com.alphagraph.risk.api.RiskScore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers "what could invalidate this investment?" — not a loss predictor, a cross-cutting
 * aggregator of what every other Phase 1 engine has already found. Pure with respect to
 * already-loaded data (docs/002_Engine_Architecture.md §5): {@code intelligence.risk} assembles
 * a {@link RiskEngineInput} from Technical (Module 1.5), Fundamental (Module 1.6), and
 * Institutional/Ownership (Module 1.7) scores plus raw market/financial figures, then calls
 * {@link #calculate}; this class never fetches anything itself.
 *
 * Event Risk (litigation, corporate governance, pledged shares, auditor resignation) is
 * deliberately absent — descoped for this module per explicit user direction, since no free
 * structured data source for any of those four signals has been confirmed real, unlike
 * everything else this engine reads.
 *
 * <p><b>Score direction.</b> Same 0-100 scale as every prior Phase 1 engine, kept in the same
 * direction for consistency across the whole system: HIGHER MEANS SAFER here too, not riskier.
 * This is achieved by making every rule's weight signed both ways — a "safe" condition
 * contributes a positive weight, a "risky" one contributes a negative weight — rather than only
 * ever subtracting from a ceiling. {@link ArithmeticRuleEvaluator} already sums whichever
 * conditions match unmodified; no risk-specific evaluator was needed.
 *
 * <p><b>Categories.</b> Rules are grouped by a name prefix (risk-business-, risk-technical-,
 * risk-ownership-, risk-valuation-), each independently scored 0-100 and banded into a
 * {@link RiskLevel}, then averaged for the overall score/level. This is an equal-weight average
 * by design — the roadmap's worked example gives no relative weighting between categories, and
 * inventing one would be asserting a judgment call the spec doesn't make.
 *
 * <p><b>Signed signals.</b> Every categorical input (Trend, Momentum, VolumeState, Profitability,
 * PromoterStatus, the three InstitutionalFlowStatus fields, DeliveryStatus) arrives as a plain
 * {@code String} — {@code risk} may never import another domain module's enum types
 * (docs/001_System_Architecture.md §4, Rule 3) — and is converted here into a +1/0/-1 signal
 * before being handed to the rule engine as an ordinary numeric metric.
 *
 * <p><b>Valuation.</b> PE and PB are computed from real inputs only, never sourced fresh: PE =
 * latestClose / eps. PB needs book value per share, which isn't a field this system stores
 * anywhere — but it can be derived from three numbers we do have. Implied shares outstanding =
 * pat / eps (since eps = pat / shares), so book value per share = totalEquity / (pat / eps) =
 * totalEquity * eps / pat. This is an approximation (assumes diluted/basic share count is stable
 * enough for the division to be meaningful) but every term in it is a real, already-sourced
 * number — no new external data point is introduced to make it work.
 */
@Component
public class RiskEngine implements Engine<RiskEngineInput, RiskScore> {

    private static final String BUSINESS_PREFIX = "risk-business-";
    private static final String TECHNICAL_PREFIX = "risk-technical-";
    private static final String OWNERSHIP_PREFIX = "risk-ownership-";
    private static final String VALUATION_PREFIX = "risk-valuation-";

    private static final int TOTAL_TRACKED_METRICS = 13;

    private final RuleEvaluator ruleEvaluator = new ArithmeticRuleEvaluator();

    @Override
    public RiskScore calculate(RiskEngineInput input, RuleSet rules) {
        Map<String, Double> metrics = buildMetrics(input);
        MetricContext context = new MetricContext(metrics);

        double businessScore = categoryScore(rules, context, BUSINESS_PREFIX);
        double technicalScore = categoryScore(rules, context, TECHNICAL_PREFIX);
        double ownershipScore = categoryScore(rules, context, OWNERSHIP_PREFIX);
        double valuationScore = categoryScore(rules, context, VALUATION_PREFIX);
        double overallScore = average(businessScore, technicalScore, ownershipScore, valuationScore);

        RiskLevel businessRisk = classify(businessScore);
        RiskLevel technicalRisk = classify(technicalScore);
        RiskLevel ownershipRisk = classify(ownershipScore);
        RiskLevel valuationRisk = classify(valuationScore);
        RiskLevel overallRisk = classify(overallScore);

        long available = metrics.size();
        double confidence = 40.0 + 60.0 * (available / (double) TOTAL_TRACKED_METRICS);

        Double peRatio = metrics.get("peRatio");
        Double pbRatio = metrics.get("pbRatio");

        return new RiskScore(
            input.instrumentId(), input.symbol(), input.asOfDate(),
            businessRisk, technicalRisk, ownershipRisk, valuationRisk, overallRisk,
            overallScore, confidence,
            peRatio, pbRatio, input.debtToEquity(),
            rules.version(), Instant.now()
        );
    }

    private Map<String, Double> buildMetrics(RiskEngineInput input) {
        Map<String, Double> metrics = new HashMap<>();

        // Business Risk
        putIfPresent(metrics, "revenueGrowthPct", input.revenueGrowthPercentage());
        putIfPresent(metrics, "profitabilityTrend", trendSignal(input.profitability(), "IMPROVING", "STABLE", "DECLINING"));
        putIfPresent(metrics, "debtToEquity", input.debtToEquity());
        putIfPresent(metrics, "cashConversionRatio", input.cashConversionRatio());

        // Technical Risk
        putIfPresent(metrics, "technicalTrendSignal", trendDirectionSignal(input.technicalTrend()));
        putIfPresent(metrics, "momentumSignal", trendSignal(input.technicalMomentum(), "IMPROVING", "NEUTRAL", "WEAKENING"));
        putIfPresent(metrics, "volumeSignal", trendSignal(input.technicalVolumeState(), "STRONG", "AVERAGE", "WEAK"));

        // Ownership Risk
        putIfPresent(metrics, "promoterTrendSignal", trendSignal(input.promoterStatus(), "ACCUMULATING", "STABLE", "DISTRIBUTING"));
        putIfPresent(metrics, "fiiTrendSignal", trendSignal(input.fiiStatus(), "BUYING", "STABLE", "SELLING"));
        putIfPresent(metrics, "mfTrendSignal", trendSignal(input.mfStatus(), "BUYING", "STABLE", "SELLING"));
        putIfPresent(metrics, "deliverySignal", deliverySignal(input.deliveryStatus()));

        // Valuation Risk - derived, not freshly sourced (see class javadoc)
        Double peRatio = peRatio(input.latestClose(), input.eps());
        Double pbRatio = pbRatio(input.latestClose(), input.eps(), input.pat(), input.totalEquity());
        putIfPresent(metrics, "peRatio", peRatio);
        putIfPresent(metrics, "pbRatio", pbRatio);

        return metrics;
    }

    private static Double peRatio(Double latestClose, Double eps) {
        if (latestClose == null || eps == null || eps == 0) {
            return null;
        }
        return latestClose / eps;
    }

    private static Double pbRatio(Double latestClose, Double eps, Double pat, Double totalEquity) {
        if (latestClose == null || eps == null || pat == null || totalEquity == null || pat == 0) {
            return null;
        }
        double bookValuePerShare = totalEquity * eps / pat;
        if (bookValuePerShare == 0) {
            return null;
        }
        return latestClose / bookValuePerShare;
    }

    /** +1 for {@code positive}, 0 for {@code neutral}, -1 for {@code negative}; null if the value matches none of them. */
    private static Double trendSignal(String value, String positive, String neutral, String negative) {
        if (value == null) {
            return null;
        }
        if (value.equals(positive)) {
            return 1.0;
        }
        if (value.equals(neutral)) {
            return 0.0;
        }
        if (value.equals(negative)) {
            return -1.0;
        }
        return null;
    }

    /** Trend has five bands, not three - STRONG_* variants collapse into the same signal as their plain counterpart. */
    private static Double trendDirectionSignal(String trend) {
        if (trend == null) {
            return null;
        }
        return switch (trend) {
            case "STRONG_UPTREND", "UPTREND" -> 1.0;
            case "SIDEWAYS" -> 0.0;
            case "DOWNTREND", "STRONG_DOWNTREND" -> -1.0;
            default -> null;
        };
    }

    /** DeliveryStatus has four bands; VERY_HIGH and HIGH both read as a genuine safety signal, so they collapse together. */
    private static Double deliverySignal(String deliveryStatus) {
        if (deliveryStatus == null) {
            return null;
        }
        return switch (deliveryStatus) {
            case "VERY_HIGH", "HIGH" -> 1.0;
            case "MODERATE" -> 0.0;
            case "LOW" -> -1.0;
            default -> null;
        };
    }

    private double categoryScore(RuleSet rules, MetricContext context, String prefix) {
        List<Rule> categoryRules = rules.rules().stream().filter(r -> r.name().startsWith(prefix)).toList();
        double rawScore = 0.0;
        for (Rule rule : categoryRules) {
            EvaluationResult result = ruleEvaluator.evaluate(rule, context);
            rawScore += result.contribution();
        }
        return clamp(50.0 + rawScore * 10.0, 0.0, 100.0);
    }

    private static void putIfPresent(Map<String, Double> metrics, String key, Double value) {
        if (value != null) {
            metrics.put(key, value);
        }
    }

    private static double average(double... values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static RiskLevel classify(double score) {
        if (score >= 80) {
            return RiskLevel.VERY_LOW;
        }
        if (score >= 60) {
            return RiskLevel.LOW;
        }
        if (score > 40) {
            return RiskLevel.MEDIUM;
        }
        if (score > 20) {
            return RiskLevel.HIGH;
        }
        return RiskLevel.VERY_HIGH;
    }
}
