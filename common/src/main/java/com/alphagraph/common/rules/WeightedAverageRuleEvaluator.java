package com.alphagraph.common.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * For engines whose inputs are themselves already-normalized composite scores (e.g. six 0-100
 * domain scores being blended into one), not raw metrics needing threshold classification.
 * {@link ArithmeticRuleEvaluator} buckets a metric against GTE/LTE/BETWEEN thresholds and sums
 * fixed condition weights on match - correct for turning raw indicators into a score, but lossy
 * when reapplied to a value that is already a 0-100 score (71 and 99 would contribute
 * identically). This evaluator instead scales {@code condition.weight()} by the metric's actual
 * value, so resolution is preserved. Every condition here is expected to use
 * {@link RuleOperator#ALWAYS} - the rule's presence in the caller's chosen prefix (e.g.
 * "decision-swing-") is what selects it, not a threshold test - but any operator is honored via
 * the same {@link RuleCondition#matches} every other evaluator uses, so a future non-ALWAYS use
 * (e.g. "only count this domain toward the average if it's above X") is not precluded.
 */
public final class WeightedAverageRuleEvaluator implements RuleEvaluator {

    @Override
    public EvaluationResult evaluate(Rule rule, MetricContext context) {
        Double value = context.get(rule.targetMetric());
        if (value == null) {
            return new EvaluationResult(rule.name(), false, 0.0, List.of());
        }

        double contribution = 0.0;
        List<RuleCondition> matched = new ArrayList<>();
        for (RuleCondition condition : rule.conditions()) {
            if (condition.matches(value)) {
                contribution += condition.weight() * value;
                matched.add(condition);
            }
        }

        return new EvaluationResult(rule.name(), true, contribution, matched);
    }
}
