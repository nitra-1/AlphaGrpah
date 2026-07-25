package com.alphagraph.common.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 0's working {@link RuleEvaluator}: sums the weight of every condition that matches the
 * rule's target metric. EQ compares with exact double equality by design — thresholds and
 * metrics are explicit configured/computed values here, not results of chained floating-point
 * arithmetic, so no tolerance is assumed on the engine's behalf.
 */
public final class ArithmeticRuleEvaluator implements RuleEvaluator {

    @Override
    public EvaluationResult evaluate(Rule rule, MetricContext context) {
        Double value = context.get(rule.targetMetric());
        if (value == null) {
            return new EvaluationResult(rule.name(), false, 0.0, List.of());
        }

        double contribution = 0.0;
        List<RuleCondition> matched = new ArrayList<>();
        for (RuleCondition condition : rule.conditions()) {
            if (matches(condition, value)) {
                contribution += condition.weight();
                matched.add(condition);
            }
        }

        return new EvaluationResult(rule.name(), true, contribution, matched);
    }

    private static boolean matches(RuleCondition condition, double value) {
        return switch (condition.operator()) {
            case GT -> value > condition.threshold();
            case LT -> value < condition.threshold();
            case GTE -> value >= condition.threshold();
            case LTE -> value <= condition.threshold();
            case EQ -> value == condition.threshold();
            case BETWEEN -> value >= condition.threshold() && value <= condition.upperBound();
        };
    }
}
