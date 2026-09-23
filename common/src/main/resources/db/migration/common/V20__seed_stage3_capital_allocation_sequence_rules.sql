-- Stage 3 Capital Allocation sequence detection thresholds
-- (docs/008_Stage3_Sequence_Detection_Specification.md §7/§14.4): same named-scalar-constant
-- convention as V16-V19's Market/Ownership/Financial/Sector Stage 3 rules - each rule has exactly
-- one ALWAYS condition; Java reads its `threshold` back directly, never runs it through
-- ArithmeticRuleEvaluator's scoring.
--
-- Units are calendar days (real corporate-action exDates), not trading sessions or reporting
-- periods - Capital Allocation is docs/008 §14.4's "event family": corporate.transformation_evidence
-- is dense daily once an instrument's first qualifying action lands, and gaps are measured against
-- real occurrence dates (days a rolling-180-day count genuinely increased), matching Stage 1's own
-- 180-day window rather than an arbitrary session count.
--
-- stage3-capital-allocation-sequence-max-age only ever applies to a cluster that never reached
-- COMPLETE at all - a completed cluster's own expiry is governed entirely by the repeat-window gap
-- rule for its type (see CapitalAllocationTransformationSequenceEngine's own javadoc).
INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('stage3-capital-return-repeat-min-count', 'requiredRepeats', 1, true),
    ('stage3-capital-return-repeat-window', 'maxGapDays', 1, true),
    ('stage3-equity-raise-repeat-min-count', 'requiredRepeats', 1, true),
    ('stage3-equity-raise-repeat-window', 'maxGapDays', 1, true),
    ('stage3-capital-allocation-sequence-max-age', 'maxAgeDays', 1, true);

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 2, 2 FROM common.rule_definitions WHERE name = 'stage3-capital-return-repeat-min-count';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 180, 180 FROM common.rule_definitions WHERE name = 'stage3-capital-return-repeat-window';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 2, 2 FROM common.rule_definitions WHERE name = 'stage3-equity-raise-repeat-min-count';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 180, 180 FROM common.rule_definitions WHERE name = 'stage3-equity-raise-repeat-window';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 540, 540 FROM common.rule_definitions WHERE name = 'stage3-capital-allocation-sequence-max-age';
