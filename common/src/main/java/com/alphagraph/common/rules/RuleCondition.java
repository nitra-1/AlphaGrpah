package com.alphagraph.common.rules;

/**
 * One threshold test within a {@link Rule}. {@code threshold} is the lower bound when
 * {@code operator} is {@link RuleOperator#BETWEEN} and {@code upperBound} is the upper bound;
 * {@code upperBound} is unused (and must be null) for every other operator.
 */
public record RuleCondition(RuleOperator operator, double threshold, Double upperBound, double weight) {

    public RuleCondition {
        if (operator == RuleOperator.BETWEEN && upperBound == null) {
            throw new IllegalArgumentException("BETWEEN condition requires an upperBound");
        }
        if (operator != RuleOperator.BETWEEN && upperBound != null) {
            throw new IllegalArgumentException(operator + " condition must not set an upperBound");
        }
    }

    public RuleCondition(RuleOperator operator, double threshold, double weight) {
        this(operator, threshold, null, weight);
    }
}
