-- Module 3.1: default rule set for the Scoring/Decision Engine (decision.engine.DecisionScoringEngine).
-- Blends the six existing domain scores into two horizon-specific composites. Every condition
-- uses the new ALWAYS operator (see V12) - weight is a blend coefficient applied directly to the
-- metric's value by common.rules.WeightedAverageRuleEvaluator, not a threshold-match weight, so
-- threshold is an unused placeholder (0) on every row here, never read.
--
-- Swing Score (short-term trading horizon): Technical-led, with Corporate catalysts, Institutional
-- flow and Sector momentum all mattering near-term; Fundamental barely moves week to week.
-- Long-Term Score (investment horizon): Fundamental-led, with Risk (safety) and Institutional
-- (promoter/FII conviction) both mattering a great deal; Sector rotation is short-term noise here.
-- Risk Score is "higher = safer" on the same 0-100 scale as every other domain (see
-- risk.api.RiskScore's own doc comment), so it needs no sign-flip to contribute positively.
-- Each rule set's weights sum to exactly 1.0, so a fully-covered instrument's composite lands
-- directly in [0, 100] with no baseline offset needed (unlike every threshold-bucket rule set,
-- e.g. V11's corporate-* rules, which need a 50-point baseline).

INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('decision-swing-technical', 'decisionTechnicalScore', 1, true),
    ('decision-swing-fundamental', 'decisionFundamentalScore', 1, true),
    ('decision-swing-institutional', 'decisionInstitutionalScore', 1, true),
    ('decision-swing-sector', 'decisionSectorScore', 1, true),
    ('decision-swing-risk', 'decisionRiskScore', 1, true),
    ('decision-swing-corporate', 'decisionCorporateScore', 1, true),
    ('decision-longterm-technical', 'decisionTechnicalScore', 1, true),
    ('decision-longterm-fundamental', 'decisionFundamentalScore', 1, true),
    ('decision-longterm-institutional', 'decisionInstitutionalScore', 1, true),
    ('decision-longterm-sector', 'decisionSectorScore', 1, true),
    ('decision-longterm-risk', 'decisionRiskScore', 1, true),
    ('decision-longterm-corporate', 'decisionCorporateScore', 1, true);

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.35 FROM common.rule_definitions WHERE name = 'decision-swing-technical';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.05 FROM common.rule_definitions WHERE name = 'decision-swing-fundamental';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.15 FROM common.rule_definitions WHERE name = 'decision-swing-institutional';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.15 FROM common.rule_definitions WHERE name = 'decision-swing-sector';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.10 FROM common.rule_definitions WHERE name = 'decision-swing-risk';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.20 FROM common.rule_definitions WHERE name = 'decision-swing-corporate';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.05 FROM common.rule_definitions WHERE name = 'decision-longterm-technical';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.35 FROM common.rule_definitions WHERE name = 'decision-longterm-fundamental';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.20 FROM common.rule_definitions WHERE name = 'decision-longterm-institutional';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.05 FROM common.rule_definitions WHERE name = 'decision-longterm-sector';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.20 FROM common.rule_definitions WHERE name = 'decision-longterm-risk';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.15 FROM common.rule_definitions WHERE name = 'decision-longterm-corporate';
