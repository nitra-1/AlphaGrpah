package com.alphagraph.common.rules;

import java.util.List;

/**
 * Outcome of evaluating one {@link Rule} against one {@link MetricContext}. If the rule's
 * target metric wasn't present in the context, {@code metricPresent} is false and
 * {@code contribution} is 0 — evaluation never throws for missing data.
 */
public record EvaluationResult(String ruleName, boolean metricPresent, double contribution,
                                List<RuleCondition> matchedConditions) {

    public EvaluationResult {
        matchedConditions = List.copyOf(matchedConditions);
    }
}
