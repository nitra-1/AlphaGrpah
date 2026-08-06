-- Module 3.1: adds RuleOperator.ALWAYS - a condition that matches unconditionally, used by
-- common.rules.WeightedAverageRuleEvaluator (decision.engine.DecisionScoringEngine's rules blend
-- six already-normalized 0-100 domain scores by weight rather than by threshold classification).

ALTER TABLE common.rule_conditions DROP CONSTRAINT rule_conditions_operator_check;

ALTER TABLE common.rule_conditions ADD CONSTRAINT rule_conditions_operator_check
    CHECK (operator IN ('GT', 'LT', 'GTE', 'LTE', 'EQ', 'BETWEEN', 'ALWAYS'));
