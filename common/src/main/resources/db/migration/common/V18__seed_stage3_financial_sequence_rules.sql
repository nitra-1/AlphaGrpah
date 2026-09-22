-- Stage 3 Financial sequence detection thresholds (docs/008_Stage3_Sequence_Detection_Specification.md
-- §7/§14.1): same named-scalar-constant convention as V16/V17's Market/Ownership Stage 3 rules -
-- each rule has exactly one ALWAYS condition; Java reads its `threshold` back directly, never runs
-- it through ArithmeticRuleEvaluator's scoring.
--
-- Units are reporting periods (financial.inflection_states rows, ascending by as_of_date - see
-- financial.transformation.FinancialInflectionHistoryReader). stage3-business-acceleration-max-gap
-- and stage3-interest-relief-max-gap are seeded at 0 (strictly consecutive) - "persistent
-- acceleration"/"trend" mean uninterrupted runs, not occurrences tolerant of a missed quarter; both
-- stay real, versioned rules so this can be loosened later once real data justifies it, not a
-- code change. stage3-multi-quarter-earnings-min-persistence is applied independently to PAT and
-- margin (never combined via OR) - see FinancialTransformationSequenceEngine's own javadoc for why
-- alternating metrics must never manufacture persistence neither one actually has.
INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('stage3-business-acceleration-max-gap', 'maxGapPeriods', 1, true),
    ('stage3-earnings-cycle-max-quarter-gap', 'maxGapPeriods', 1, true),
    ('stage3-multi-quarter-earnings-min-persistence', 'minPersistencePeriods', 1, true),
    ('stage3-interest-relief-max-gap', 'maxGapPeriods', 1, true),
    ('stage3-financial-sequence-max-age', 'maxAgePeriods', 1, true);

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0 FROM common.rule_definitions WHERE name = 'stage3-business-acceleration-max-gap';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 4, 4 FROM common.rule_definitions WHERE name = 'stage3-earnings-cycle-max-quarter-gap';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 2, 2 FROM common.rule_definitions WHERE name = 'stage3-multi-quarter-earnings-min-persistence';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0 FROM common.rule_definitions WHERE name = 'stage3-interest-relief-max-gap';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 6, 6 FROM common.rule_definitions WHERE name = 'stage3-financial-sequence-max-age';
