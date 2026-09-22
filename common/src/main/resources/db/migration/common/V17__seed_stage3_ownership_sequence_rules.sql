-- Stage 3 Ownership sequence detection thresholds (docs/008_Stage3_Sequence_Detection_Specification.md
-- §7/§14.2): same named-scalar-constant convention as V16's Market Stage 3 rules - each rule has
-- exactly one ALWAYS condition; Java reads its `threshold` back directly, never runs it through
-- ArithmeticRuleEvaluator's scoring.
--
-- Units are reporting periods (distinct ownership.transformation_states latest_period_end values -
-- see ownership.transformation.OwnershipInflectionHistoryReader's canonicalization), never calendar
-- days and never raw availability-recompute rows. Small integers, appropriate for genuinely sparse
-- quarterly data (avg 2.2-4.9 real observations/instrument for the 7 XBRL-gated metrics) - not
-- Market's session-scale numbers.
INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('stage3-ownership-building-max-gap', 'maxGapPeriods', 1, true),
    ('stage3-ownership-building-min-persistence', 'minPersistencePeriods', 1, true),
    ('stage3-ownership-contradiction-tolerance', 'contradictionTolerancePeriods', 1, true),
    ('stage3-broad-participation-max-span', 'maxSpanPeriods', 1, true),
    ('stage3-promoter-alignment-max-span', 'maxSpanPeriods', 1, true),
    ('stage3-ownership-sequence-max-age', 'maxAgePeriods', 1, true);

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 2, 2 FROM common.rule_definitions WHERE name = 'stage3-ownership-building-max-gap';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 2, 2 FROM common.rule_definitions WHERE name = 'stage3-ownership-building-min-persistence';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 2, 2 FROM common.rule_definitions WHERE name = 'stage3-ownership-contradiction-tolerance';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 2, 2 FROM common.rule_definitions WHERE name = 'stage3-broad-participation-max-span';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 2, 2 FROM common.rule_definitions WHERE name = 'stage3-promoter-alignment-max-span';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 6, 6 FROM common.rule_definitions WHERE name = 'stage3-ownership-sequence-max-age';
