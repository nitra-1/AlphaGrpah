-- Stage 3 Market sequence detection thresholds (docs/008_Stage3_Sequence_Detection_Specification.md
-- §7/§14.3): named scalar constants, not metric-scoring ladders - a disclosed, lighter-weight use
-- of common.rule_definitions/rule_conditions than every prior seed file in this directory (which
-- all evaluate a real metric against a weighted GTE/ALWAYS ladder to produce a 0-100 score). Each
-- rule here has exactly one ALWAYS condition; Java reads its `threshold` back directly as the
-- configured number of trading sessions, never runs it through ArithmeticRuleEvaluator's scoring.
--
-- All 4 values are the docs/008 §14.3 starting points verbatim - explicitly disclosed there as
-- starting values to revisit once real Market Stage 3 data accumulates, not final tuning.
INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('stage3-delivery-led-accumulation-max-gap', 'maxGapSessions', 1, true),
    ('stage3-stealth-formation-max-window', 'maxGapSessions', 1, true),
    ('stage3-market-recognition-max-gap', 'maxGapSessions', 1, true),
    ('stage3-sequence-max-age', 'maxAgeSessions', 1, true);

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 3, 3 FROM common.rule_definitions WHERE name = 'stage3-delivery-led-accumulation-max-gap';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 20, 20 FROM common.rule_definitions WHERE name = 'stage3-stealth-formation-max-window';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 10, 10 FROM common.rule_definitions WHERE name = 'stage3-market-recognition-max-gap';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 40, 40 FROM common.rule_definitions WHERE name = 'stage3-sequence-max-age';
