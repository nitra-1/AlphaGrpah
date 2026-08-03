package com.alphagraph.corporate.orderbook;

import com.alphagraph.common.engine.Engine;
import com.alphagraph.common.rules.ArithmeticRuleEvaluator;
import com.alphagraph.common.rules.EvaluationResult;
import com.alphagraph.common.rules.MetricContext;
import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleEvaluator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.corporate.api.OrderBookEntry;
import com.alphagraph.corporate.api.OrderBookSnapshot;
import com.alphagraph.corporate.api.OrderLifecycleStage;
import com.alphagraph.corporate.api.OrderQuality;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates one instrument's order-lifecycle ledger into a point-in-time {@link OrderBookSnapshot} -
 * a genuine {@code common.engine.Engine} implementation, unlike Module 2.3's topic-matching
 * classifier, since this is exactly the "score clean numeric metrics via RuleSet threshold rules"
 * shape every Phase 1 engine already has.
 *
 * <p><b>Ledger model, and its real limitation.</b> Without a stable order-reference number in the
 * extracted facts, there is no reliable way to match a later COMPLETION or CANCELLATION event back
 * to the specific NEW_ORDER/TENDER_WIN event it closes out - two separate documents describing
 * "the same" order carry no shared identifier. This engine therefore treats the ledger as a plain
 * net debits/credits account (NEW_ORDER/TENDER_WIN add, COMPLETION/CANCELLATION subtract) rather
 * than tracking matched order pairs - a real, disclosed simplification, not a fabricated precision
 * this data doesn't support.
 */
@Component
class OrderBookAggregationEngine implements Engine<OrderBookAggregationInput, OrderBookSnapshot> {

    private static final Set<OrderLifecycleStage> ADDS = Set.of(OrderLifecycleStage.NEW_ORDER, OrderLifecycleStage.TENDER_WIN);
    private static final Set<OrderLifecycleStage> SUBTRACTS = Set.of(OrderLifecycleStage.COMPLETION, OrderLifecycleStage.CANCELLATION);
    private static final Set<OrderLifecycleStage> ACTIVE = Set.of(
        OrderLifecycleStage.NEW_ORDER, OrderLifecycleStage.TENDER_WIN, OrderLifecycleStage.EXECUTION_UPDATE
    );

    private final RuleEvaluator ruleEvaluator = new ArithmeticRuleEvaluator();

    @Override
    public OrderBookSnapshot calculate(OrderBookAggregationInput input, RuleSet rules) {
        double currentOrderBookCrore = netOrderBookValue(input.entries());
        double executionVisibilityYears = weightedExecutionVisibility(input.entries(), input.asOfDate().getYear());
        int orderCount = Math.max(0, countByStage(input.entries(), ADDS) - countByStage(input.entries(), SUBTRACTS));
        Double growthPct = growthPercent(currentOrderBookCrore, input.previousOrderBookCrore());

        Map<String, Double> metrics = new HashMap<>();
        if (growthPct != null) {
            metrics.put("orderBookGrowthPct", growthPct);
        }
        metrics.put("executionVisibilityYears", executionVisibilityYears);
        metrics.put("orderCount", (double) orderCount);

        MetricContext context = new MetricContext(metrics);
        double rawScore = 0.0;
        for (Rule rule : rules.rules()) {
            EvaluationResult result = ruleEvaluator.evaluate(rule, context);
            rawScore += result.contribution();
        }
        double qualityScore = clamp(50.0 + rawScore * 10.0, 0.0, 100.0);
        OrderQuality orderQuality = classify(qualityScore);

        double confidence = confidence(input.entries());

        return new OrderBookSnapshot(
            input.instrumentId(), input.symbol(), input.asOfDate(),
            currentOrderBookCrore, growthPct, executionVisibilityYears, orderCount,
            orderQuality, qualityScore, confidence, rules.version(), Instant.now()
        );
    }

    private static double netOrderBookValue(List<OrderBookEntry> entries) {
        double total = 0.0;
        for (OrderBookEntry entry : entries) {
            if (entry.orderValueCrore() == null) {
                continue;
            }
            if (ADDS.contains(entry.lifecycleStage())) {
                total += entry.orderValueCrore();
            } else if (SUBTRACTS.contains(entry.lifecycleStage())) {
                total -= entry.orderValueCrore();
            }
        }
        return total;
    }

    private static double weightedExecutionVisibility(List<OrderBookEntry> entries, int currentYear) {
        double weightedYearsSum = 0.0;
        double weightSum = 0.0;
        for (OrderBookEntry entry : entries) {
            if (!ACTIVE.contains(entry.lifecycleStage()) || entry.orderValueCrore() == null) {
                continue;
            }
            Integer endYear = parseYear(entry.executionEnd());
            if (endYear == null) {
                continue;
            }
            double yearsRemaining = endYear - currentYear;
            if (yearsRemaining <= 0) {
                continue;
            }
            weightedYearsSum += yearsRemaining * entry.orderValueCrore();
            weightSum += entry.orderValueCrore();
        }
        return weightSum == 0 ? 0.0 : weightedYearsSum / weightSum;
    }

    private static Integer parseYear(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int countByStage(List<OrderBookEntry> entries, Set<OrderLifecycleStage> stages) {
        return (int) entries.stream().filter(e -> stages.contains(e.lifecycleStage())).count();
    }

    private static Double growthPercent(double current, Double previous) {
        if (previous == null || previous == 0) {
            return null;
        }
        return (current - previous) / previous * 100.0;
    }

    private static double confidence(List<OrderBookEntry> entries) {
        if (entries.isEmpty()) {
            return 50.0;
        }
        long withValue = entries.stream().filter(e -> e.orderValueCrore() != null).count();
        return 50.0 + 50.0 * ((double) withValue / entries.size());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static OrderQuality classify(double score) {
        if (score >= 80) {
            return OrderQuality.EXCELLENT;
        }
        if (score >= 60) {
            return OrderQuality.GOOD;
        }
        if (score > 40) {
            return OrderQuality.FAIR;
        }
        return OrderQuality.POOR;
    }
}
