package com.alphagraph.common.rules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedAverageRuleEvaluatorTest {

    private final WeightedAverageRuleEvaluator evaluator = new WeightedAverageRuleEvaluator();

    private static MetricContext contextWith(double score) {
        return new MetricContext(Map.of("technicalScore", score));
    }

    @Test
    void alwaysOperatorMatchesRegardlessOfValue() {
        Rule rule = new Rule("technical-weight", "technicalScore", 1, List.of(
            new RuleCondition(RuleOperator.ALWAYS, 0.0, 0.35)
        ));

        assertThat(evaluator.evaluate(rule, contextWith(0.0)).metricPresent()).isTrue();
        assertThat(evaluator.evaluate(rule, contextWith(100.0)).metricPresent()).isTrue();
    }

    @Test
    void contributionScalesWithBothWeightAndValue() {
        Rule rule = new Rule("technical-weight", "technicalScore", 1, List.of(
            new RuleCondition(RuleOperator.ALWAYS, 0.0, 0.35)
        ));

        EvaluationResult result = evaluator.evaluate(rule, contextWith(80.0));

        assertThat(result.contribution()).isEqualTo(28.0); // 0.35 * 80
    }

    @Test
    void higherValueContributesMoreThanLowerValueAtTheSameWeight() {
        Rule rule = new Rule("technical-weight", "technicalScore", 1, List.of(
            new RuleCondition(RuleOperator.ALWAYS, 0.0, 0.5)
        ));

        double lowContribution = evaluator.evaluate(rule, contextWith(71.0)).contribution();
        double highContribution = evaluator.evaluate(rule, contextWith(99.0)).contribution();

        assertThat(highContribution).isGreaterThan(lowContribution);
        assertThat(highContribution - lowContribution).isEqualTo(14.0); // 0.5 * (99 - 71)
    }

    @Test
    void missingTargetMetricNeverThrowsAndContributesZero() {
        Rule rule = new Rule("technical-weight", "technicalScore", 1, List.of(
            new RuleCondition(RuleOperator.ALWAYS, 0.0, 0.35)
        ));
        MetricContext emptyContext = new MetricContext(Map.of());

        EvaluationResult result = evaluator.evaluate(rule, emptyContext);

        assertThat(result.metricPresent()).isFalse();
        assertThat(result.contribution()).isZero();
        assertThat(result.matchedConditions()).isEmpty();
    }

    @Test
    void nonAlwaysOperatorsStillHonorTheirThreshold() {
        Rule rule = new Rule("gate", "technicalScore", 1, List.of(
            new RuleCondition(RuleOperator.GTE, 50.0, 0.5)
        ));

        assertThat(evaluator.evaluate(rule, contextWith(60.0)).contribution()).isEqualTo(30.0); // matches, 0.5 * 60
        assertThat(evaluator.evaluate(rule, contextWith(40.0)).contribution()).isZero(); // below threshold, no match
    }
}
