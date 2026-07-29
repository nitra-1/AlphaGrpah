package com.alphagraph.financial.engine;

import com.alphagraph.common.engine.Engine;
import com.alphagraph.common.rules.ArithmeticRuleEvaluator;
import com.alphagraph.common.rules.EvaluationResult;
import com.alphagraph.common.rules.MetricContext;
import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleEvaluator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.financial.api.BusinessGrowth;
import com.alphagraph.financial.api.CapitalEfficiency;
import com.alphagraph.financial.api.FinancialQuality;
import com.alphagraph.financial.api.FinancialResult;
import com.alphagraph.financial.api.FundamentalEngineInput;
import com.alphagraph.financial.api.FundamentalScore;
import com.alphagraph.financial.api.Profitability;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Answers "is the business becoming stronger?" from financial statements alone - no price, no
 * charts (mirrors the Technical Engine's price-only mandate from Module 1.5, inverted). Pure with
 * respect to already-loaded data (docs/002_Engine_Architecture.md §5): unlike Module 1.5, this
 * engine's data already lives in its own module, so {@code FundamentalAnalysisOrchestrator}
 * assembles the input directly from {@link FinancialResultReader} - no {@code intelligence}
 * bridging needed.
 *
 * The raw-score-to-0-100 {@code financialScore} mapping ({@code 50 + rawScore * 10}, clamped) is
 * an engine implementation choice, not itself a {@code Rule} - same convention as
 * technical.engine.TechnicalEngine. The individual threshold/weight decisions that produce
 * {@code rawScore} are rules (seeded in common.rule_definitions, Module 1.6's migration), so
 * those remain editable without a redeploy.
 */
@Component
public class FundamentalEngine implements Engine<FundamentalEngineInput, FundamentalScore> {

    private static final int TOTAL_TRACKED_METRICS = 8;

    private final RuleEvaluator ruleEvaluator = new ArithmeticRuleEvaluator();

    @Override
    public FundamentalScore calculate(FundamentalEngineInput input, RuleSet rules) {
        List<FinancialResult> periods = input.periodsAscending();
        if (periods.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute a fundamental score with zero periods: " + input.symbol());
        }

        FinancialResult current = periods.get(periods.size() - 1);
        Optional<FinancialResult> prior = findYoyPrior(periods, current);

        Double revenueGrowthPct = prior.map(p -> growthPct(current.sales(), p.sales())).orElse(null);
        Double patGrowthPct = prior.map(p -> growthPct(current.pat(), p.pat())).orElse(null);
        Double epsGrowthPct = (current.eps() != null && prior.isPresent() && prior.get().eps() != null)
            ? growthPct(current.eps(), prior.get().eps())
            : null;

        Double roePercentage = toDouble(current.roePercentage());
        Double rocePercentage = toDouble(current.rocePercentage());
        Double operatingMarginPercentage = toDouble(current.operatingMarginPercentage());
        Double netMarginPercentage = toDouble(current.netMarginPercentage());

        Double assetTurnover = ratio(current.sales(), current.totalAssets());
        Double workingCapital = (current.currentAssets() != null && current.currentLiabilities() != null)
            ? current.currentAssets().subtract(current.currentLiabilities()).doubleValue()
            : null;
        Double cashConversionRatio = ratio(current.cashFlowFromOperations(), current.pat());
        Double debtToEquity = ratio(current.totalDebt(), current.totalEquity());
        Double interestCoverage = ratio(current.ebit(), current.interestExpense());

        double financialScore = scoreFromRules(
            rules, revenueGrowthPct, patGrowthPct, roePercentage, rocePercentage,
            netMarginPercentage, assetTurnover, interestCoverage, debtToEquity
        );

        BusinessGrowth businessGrowth = classifyGrowth(revenueGrowthPct, patGrowthPct);
        Profitability profitability = classifyProfitability(current, prior.orElse(null), netMarginPercentage);
        CapitalEfficiency capitalEfficiency = classifyEfficiency(assetTurnover, rocePercentage);
        FinancialQuality financialQuality = classifyQuality(roePercentage, debtToEquity);

        double confidence = confidenceFrom(
            revenueGrowthPct, patGrowthPct, roePercentage, rocePercentage,
            netMarginPercentage, assetTurnover, interestCoverage, debtToEquity
        );

        return new FundamentalScore(
            input.instrumentId(), input.symbol(), current.periodEnd(),
            businessGrowth, profitability, capitalEfficiency, financialQuality,
            financialScore, confidence,
            revenueGrowthPct, patGrowthPct, epsGrowthPct,
            roePercentage, rocePercentage, operatingMarginPercentage, netMarginPercentage,
            assetTurnover, workingCapital, cashConversionRatio, debtToEquity, interestCoverage,
            rules.version(), Instant.now()
        );
    }

    /** Most recent period with the same period_type strictly before the current one - a YoY comparison, not just "previous row". */
    private static Optional<FinancialResult> findYoyPrior(List<FinancialResult> periods, FinancialResult current) {
        return periods.stream()
            .filter(p -> p != current)
            .filter(p -> p.periodType().equals(current.periodType()))
            .filter(p -> p.periodEnd().isBefore(current.periodEnd()))
            .max(Comparator.comparing(FinancialResult::periodEnd));
    }

    private static double growthPct(BigDecimal currentValue, BigDecimal priorValue) {
        if (priorValue.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return currentValue.subtract(priorValue).divide(priorValue.abs(), java.math.MathContext.DECIMAL64).doubleValue() * 100.0;
    }

    private static Double ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, java.math.MathContext.DECIMAL64).doubleValue();
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private double scoreFromRules(
        RuleSet rules, Double revenueGrowthPct, Double patGrowthPct, Double roePercentage, Double rocePercentage,
        Double netMarginPercentage, Double assetTurnover, Double interestCoverage, Double debtToEquity
    ) {
        Map<String, Double> metrics = new HashMap<>();
        putIfPresent(metrics, "revenueGrowthPct", revenueGrowthPct);
        putIfPresent(metrics, "patGrowthPct", patGrowthPct);
        putIfPresent(metrics, "roePercentage", roePercentage);
        putIfPresent(metrics, "rocePercentage", rocePercentage);
        putIfPresent(metrics, "netMarginPercentage", netMarginPercentage);
        putIfPresent(metrics, "assetTurnover", assetTurnover);
        putIfPresent(metrics, "interestCoverage", interestCoverage);
        putIfPresent(metrics, "debtToEquity", debtToEquity);

        MetricContext context = new MetricContext(metrics);
        double rawScore = 0;
        for (Rule rule : rules.rules()) {
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static BusinessGrowth classifyGrowth(Double revenueGrowthPct, Double patGrowthPct) {
        if (revenueGrowthPct == null && patGrowthPct == null) {
            return BusinessGrowth.MODERATE;
        }
        double avg = averageOfPresent(revenueGrowthPct, patGrowthPct);
        if (avg > 20) {
            return BusinessGrowth.EXCELLENT;
        }
        if (avg > 10) {
            return BusinessGrowth.GOOD;
        }
        if (avg >= 0) {
            return BusinessGrowth.MODERATE;
        }
        if (avg >= -10) {
            return BusinessGrowth.WEAK;
        }
        return BusinessGrowth.DECLINING;
    }

    private static double averageOfPresent(Double a, Double b) {
        if (a != null && b != null) {
            return (a + b) / 2.0;
        }
        return a != null ? a : b;
    }

    /**
     * IMPROVING/DECLINING only when the same period's margin can be compared to a same-type prior
     * period; otherwise STABLE is the honest default - there's no trend to report without two
     * points, and guessing one from a single period's absolute level would be inventing a trend.
     */
    private static Profitability classifyProfitability(FinancialResult current, FinancialResult prior, Double currentNetMargin) {
        if (prior == null || prior.netMarginPercentage() == null || currentNetMargin == null) {
            return Profitability.STABLE;
        }
        double priorMargin = prior.netMarginPercentage().doubleValue();
        double delta = currentNetMargin - priorMargin;
        if (delta > 1.0) {
            return Profitability.IMPROVING;
        }
        if (delta < -1.0) {
            return Profitability.DECLINING;
        }
        return Profitability.STABLE;
    }

    private static CapitalEfficiency classifyEfficiency(Double assetTurnover, Double rocePercentage) {
        if (assetTurnover != null) {
            if (assetTurnover > 1.0) {
                return CapitalEfficiency.HIGH;
            }
            if (assetTurnover > 0.5) {
                return CapitalEfficiency.MODERATE;
            }
            return CapitalEfficiency.LOW;
        }
        if (rocePercentage != null) {
            if (rocePercentage > 20) {
                return CapitalEfficiency.HIGH;
            }
            if (rocePercentage > 10) {
                return CapitalEfficiency.MODERATE;
            }
            return CapitalEfficiency.LOW;
        }
        return CapitalEfficiency.MODERATE;
    }

    private static FinancialQuality classifyQuality(Double roePercentage, Double debtToEquity) {
        boolean lowLeverage = debtToEquity == null || debtToEquity < 1.0;
        boolean highLeverage = debtToEquity != null && debtToEquity > 1.5;

        if (roePercentage != null && roePercentage > 20 && lowLeverage) {
            return FinancialQuality.STRONG;
        }
        if ((roePercentage != null && roePercentage < 10) || highLeverage) {
            return FinancialQuality.WEAK;
        }
        return FinancialQuality.ADEQUATE;
    }

    private static double confidenceFrom(Double... metrics) {
        long available = java.util.Arrays.stream(metrics).filter(m -> m != null).count();
        return 40.0 + 60.0 * (available / (double) TOTAL_TRACKED_METRICS);
    }
}
