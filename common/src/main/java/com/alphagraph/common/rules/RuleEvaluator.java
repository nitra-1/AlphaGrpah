package com.alphagraph.common.rules;

/** Pure function: no side effects, no I/O. Never fetches data — {@link MetricContext} already has it. */
public interface RuleEvaluator {

    EvaluationResult evaluate(Rule rule, MetricContext context);
}
