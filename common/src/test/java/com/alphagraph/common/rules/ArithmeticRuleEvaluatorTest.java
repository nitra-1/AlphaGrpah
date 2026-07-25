package com.alphagraph.common.rules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ArithmeticRuleEvaluatorTest {

    private final ArithmeticRuleEvaluator evaluator = new ArithmeticRuleEvaluator();

    private static MetricContext contextWith(double rsi) {
        return new MetricContext(Map.of("rsi", rsi));
    }

    @Test
    void gtMatchesStrictlyAboveThreshold() {
        Rule rule = new Rule("overbought", "rsi", 1, List.of(new RuleCondition(RuleOperator.GT, 70.0, 1.0)));

        assertThat(evaluator.evaluate(rule, contextWith(70.1)).contribution()).isEqualTo(1.0);
        assertThat(evaluator.evaluate(rule, contextWith(70.0)).contribution()).isZero();
    }

    @Test
    void ltMatchesStrictlyBelowThreshold() {
        Rule rule = new Rule("oversold", "rsi", 1, List.of(new RuleCondition(RuleOperator.LT, 30.0, 1.0)));

        assertThat(evaluator.evaluate(rule, contextWith(29.9)).contribution()).isEqualTo(1.0);
        assertThat(evaluator.evaluate(rule, contextWith(30.0)).contribution()).isZero();
    }

    @Test
    void gteAndLteAreInclusiveAtTheBoundary() {
        Rule gteRule = new Rule("gte", "rsi", 1, List.of(new RuleCondition(RuleOperator.GTE, 70.0, 1.0)));
        Rule lteRule = new Rule("lte", "rsi", 1, List.of(new RuleCondition(RuleOperator.LTE, 30.0, 1.0)));

        assertThat(evaluator.evaluate(gteRule, contextWith(70.0)).contribution()).isEqualTo(1.0);
        assertThat(evaluator.evaluate(lteRule, contextWith(30.0)).contribution()).isEqualTo(1.0);
    }

    @Test
    void eqMatchesExactValueOnly() {
        Rule rule = new Rule("exact", "rsi", 1, List.of(new RuleCondition(RuleOperator.EQ, 50.0, 1.0)));

        assertThat(evaluator.evaluate(rule, contextWith(50.0)).contribution()).isEqualTo(1.0);
        assertThat(evaluator.evaluate(rule, contextWith(50.01)).contribution()).isZero();
    }

    @Test
    void betweenIsInclusiveOnBothBoundsAndExcludesOutsideTheRange() {
        Rule rule = new Rule("neutral", "rsi", 1, List.of(
            new RuleCondition(RuleOperator.BETWEEN, 40.0, 60.0, 1.0)
        ));

        assertThat(evaluator.evaluate(rule, contextWith(40.0)).contribution()).isEqualTo(1.0);
        assertThat(evaluator.evaluate(rule, contextWith(60.0)).contribution()).isEqualTo(1.0);
        assertThat(evaluator.evaluate(rule, contextWith(50.0)).contribution()).isEqualTo(1.0);
        assertThat(evaluator.evaluate(rule, contextWith(39.9)).contribution()).isZero();
        assertThat(evaluator.evaluate(rule, contextWith(60.1)).contribution()).isZero();
    }

    @Test
    void multipleConditionsSumTheWeightsOfOnlyTheOnesThatMatch() {
        Rule rule = new Rule("rsi-ladder", "rsi", 1, List.of(
            new RuleCondition(RuleOperator.GT, 70.0, 1.0),   // matches at 75 -> +1.0
            new RuleCondition(RuleOperator.GT, 80.0, 0.5),   // does not match at 75
            new RuleCondition(RuleOperator.BETWEEN, 70.0, 90.0, 0.25) // matches at 75 -> +0.25
        ));

        EvaluationResult result = evaluator.evaluate(rule, contextWith(75.0));

        assertThat(result.metricPresent()).isTrue();
        assertThat(result.contribution()).isEqualTo(1.25);
        assertThat(result.matchedConditions()).hasSize(2);
    }

    @Test
    void missingTargetMetricNeverThrowsAndContributesZero() {
        Rule rule = new Rule("overbought", "rsi", 1, List.of(new RuleCondition(RuleOperator.GT, 70.0, 1.0)));
        MetricContext emptyContext = new MetricContext(Map.of());

        EvaluationResult result = evaluator.evaluate(rule, emptyContext);

        assertThat(result.metricPresent()).isFalse();
        assertThat(result.contribution()).isZero();
        assertThat(result.matchedConditions()).isEmpty();
    }

    @Test
    void betweenConditionRequiresAnUpperBound() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RuleCondition(RuleOperator.BETWEEN, 40.0, null, 1.0));
    }

    @Test
    void nonBetweenConditionMustNotSetAnUpperBound() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RuleCondition(RuleOperator.GT, 40.0, 50.0, 1.0));
    }
}
