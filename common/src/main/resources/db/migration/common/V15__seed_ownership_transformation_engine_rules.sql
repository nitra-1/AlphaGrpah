-- Default rule set for the Ownership Transformation Engine
-- (ownership.transformation.OwnershipTransformationEngine), the first Multibagger Discovery
-- evidence-family vertical slice. Single-pass ArithmeticRuleEvaluator per metric (no Pass 2 blend
-- like deal-materiality's - each metric maps to its own independent state, never combined into one
-- number), same cumulative-ladder shape V14's deal-materiality-adtv-ratio rule uses: one ALWAYS
-- base condition plus GTE step-ups sum to a 0-100 "signal strength" score as the metric's absolute
-- quarter-over-quarter change in percentage points crosses each threshold.
--
-- All three metrics (promoter/FII/DII change) start from the identical ladder below - a
-- deliberate first-pass placeholder, not a claim that promoter, FII, and DII moves are equally
-- significant at the same magnitude. Real observed distributions across the tracked universe
-- should be compared once live data accumulates, and these thresholds revisited then - same
-- discipline deal-materiality-adtv-ratio's boundaries used (confirmed against real data, not
-- guessed once and left alone).
--
-- Band shape: [0, 0.25)=10, [0.25, 0.50)=25, [0.50, 1.00)=45, [1.00, 2.00)=70, [2.00, inf)=100.
-- A score >= 30 is treated (in Java, ownership.transformation.OwnershipTransformationEngine) as
-- crossing the "this is a real signal, not noise" threshold - i.e. a change of roughly half a
-- percentage point or more.

INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('ownership-transformation-promoter-change', 'absChangePp', 1, true),
    ('ownership-transformation-fii-change', 'absChangePp', 1, true),
    ('ownership-transformation-dii-change', 'absChangePp', 1, true);

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 10 FROM common.rule_definitions WHERE name = 'ownership-transformation-promoter-change';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 0.25, 15 FROM common.rule_definitions WHERE name = 'ownership-transformation-promoter-change';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 0.50, 20 FROM common.rule_definitions WHERE name = 'ownership-transformation-promoter-change';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 1.00, 25 FROM common.rule_definitions WHERE name = 'ownership-transformation-promoter-change';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 2.00, 30 FROM common.rule_definitions WHERE name = 'ownership-transformation-promoter-change';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 10 FROM common.rule_definitions WHERE name = 'ownership-transformation-fii-change';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 0.25, 15 FROM common.rule_definitions WHERE name = 'ownership-transformation-fii-change';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 0.50, 20 FROM common.rule_definitions WHERE name = 'ownership-transformation-fii-change';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 1.00, 25 FROM common.rule_definitions WHERE name = 'ownership-transformation-fii-change';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 2.00, 30 FROM common.rule_definitions WHERE name = 'ownership-transformation-fii-change';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 10 FROM common.rule_definitions WHERE name = 'ownership-transformation-dii-change';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 0.25, 15 FROM common.rule_definitions WHERE name = 'ownership-transformation-dii-change';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 0.50, 20 FROM common.rule_definitions WHERE name = 'ownership-transformation-dii-change';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 1.00, 25 FROM common.rule_definitions WHERE name = 'ownership-transformation-dii-change';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 2.00, 30 FROM common.rule_definitions WHERE name = 'ownership-transformation-dii-change';
