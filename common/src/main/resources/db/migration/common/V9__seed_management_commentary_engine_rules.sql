-- Module 2.5: default rule set for the Management Commentary Engine
-- (corporate.commentary.ManagementCommentaryEngine). Same pattern as V3-V8: distinguished by a
-- "management-" name prefix. Single overall Growth Visibility score (like Order Book's Order
-- Quality), not multiple categories.
--
-- guidancePersistenceQuarters and commitmentStrengthSignal are absent from the metric context
-- entirely when there isn't enough observation history to compute them (fewer than 2 numeric
-- revenue-guidance observations) - ArithmeticRuleEvaluator treats a missing target metric as a
-- non-match (0 contribution), so these rules simply don't contribute until enough history exists;
-- this is not a fabricated neutral value.
--
-- Weights chosen so the best-case raw sum is exactly 3.0 (score 80) and the worst-case is -3.0
-- (score 20), matching every prior rule set's convention.

INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('management-guidance-direction', 'guidanceDirectionSignal', 1, true),
    ('management-guidance-persistence', 'guidancePersistenceQuarters', 1, true),
    ('management-commitment-strength', 'commitmentStrengthSignal', 1, true);

-- Direction: most recent revenue guidance being genuinely positive (+1) or negative (-1) is the
-- strongest single signal of near-term growth visibility.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 1, 1.5 FROM common.rule_definitions WHERE name = 'management-guidance-direction';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', -1, -1.5 FROM common.rule_definitions WHERE name = 'management-guidance-direction';

-- Persistence: 3+ consecutive quarters of positive guidance is real, sustained visibility, not a
-- one-off; a reversal after a run of positive quarters (0 consecutive) is a real concern.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 3, 1.0 FROM common.rule_definitions WHERE name = 'management-guidance-persistence';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', 0, -1.0 FROM common.rule_definitions WHERE name = 'management-guidance-persistence';

-- Commitment strength: management using confident, unhedged language (HIGH/VERY_HIGH = 2/3) is a
-- real signal distinct from direction itself; hedged language (LOW = 0) undercuts an otherwise
-- positive-sounding statement.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 2, 0.5 FROM common.rule_definitions WHERE name = 'management-commitment-strength';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', 0, -0.5 FROM common.rule_definitions WHERE name = 'management-commitment-strength';
