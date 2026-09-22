-- Stage 3 Sector sequence detection thresholds (docs/008_Stage3_Sequence_Detection_Specification.md
-- §7/§14.5): same named-scalar-constant convention as V16-V18's Market/Ownership/Financial Stage 3
-- rules - each rule has exactly one ALWAYS condition; Java reads its `threshold` back directly,
-- never runs it through ArithmeticRuleEvaluator's scoring.
--
-- Units are trading sessions (sector.inflection_states rows, ascending by as_of_date - Sector is a
-- daily family like Market, not a quarterly one like Ownership/Financial).
-- stage3-sector-contrast-max-evidence-lag is new, not doc-named - it bounds how far apart
-- SECTOR_RELATIVE_STRENGTH's and INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR's own real evidence dates
-- may be before IDIOSYNCRATIC_LEADERSHIP's contrast is considered too stale to trust (see
-- SectorTransformationSequenceEngine's own javadoc for why absence of a reason code alone is never
-- sufficient - it can mean "genuinely not rising" or merely "no current information").
INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('stage3-sector-tailwind-max-gap', 'maxGapSessions', 1, true),
    ('stage3-leadership-emergence-max-gap', 'maxGapSessions', 1, true),
    ('stage3-sector-sequence-max-age', 'maxAgeSessions', 1, true),
    ('stage3-sector-contrast-max-evidence-lag', 'maxLagSessions', 1, true);

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 15, 15 FROM common.rule_definitions WHERE name = 'stage3-sector-tailwind-max-gap';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 30, 30 FROM common.rule_definitions WHERE name = 'stage3-leadership-emergence-max-gap';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 40, 40 FROM common.rule_definitions WHERE name = 'stage3-sector-sequence-max-age';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 5, 5 FROM common.rule_definitions WHERE name = 'stage3-sector-contrast-max-evidence-lag';
