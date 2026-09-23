-- Stage 4 Cross-Domain Convergence thresholds (docs/008_Stage3_Sequence_Detection_Specification.md
-- hand-off point + the user's own Stage 4 spec): same named-scalar-constant convention as V16-V20's
-- Stage 3 sequence rules - each rule has exactly one ALWAYS condition; Java reads its `threshold`
-- back directly, never runs it through ArithmeticRuleEvaluator's scoring.
--
-- Piecewise band tables (breadth/recency/density normalization, phase factors/weights, per-trigger
-- contradiction penalties) are deliberately NOT rules here - common.rule_definitions/rule_conditions
-- has no first-class "band table" shape, so these stay documented Java constants in
-- DiscoveryConvergenceEngine, the same tradeoff every existing engine already made for its own
-- step-weight arrays. Only genuine scalar thresholds/weights are rules.
--
-- stage4-risk-contradiction-max-age-days is new, not doc-named - without it, a contradiction from
-- months ago would silently keep penalizing today's convergence forever if Risk Stage 2 hasn't
-- written a fresher state since (see DiscoveryConvergenceEngine's own javadoc).
INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('stage4-min-domains-early-convergence', 'minDomainsEarlyConvergence', 1, true),
    ('stage4-min-domains-multidomain', 'minDomainsMultidomain', 1, true),
    ('stage4-strong-convergence-score-threshold', 'strongConvergenceScoreThreshold', 1, true),
    ('stage4-multidomain-score-threshold', 'multidomainScoreThreshold', 1, true),
    ('stage4-convergence-max-span-days', 'maxSpanDays', 1, true),
    ('stage4-market-max-age-days', 'maxAgeDays', 1, true),
    ('stage4-sector-max-age-days', 'maxAgeDays', 1, true),
    ('stage4-financial-max-age-days', 'maxAgeDays', 1, true),
    ('stage4-ownership-max-age-days', 'maxAgeDays', 1, true),
    ('stage4-capital-max-age-days', 'maxAgeDays', 1, true),
    ('stage4-max-contradiction-penalty', 'maxContradictionPenalty', 1, true),
    ('stage4-risk-contradiction-max-age-days', 'maxAgeDays', 1, true),
    ('stage4-weight-breadth', 'weight', 1, true),
    ('stage4-weight-maturity', 'weight', 1, true),
    ('stage4-weight-recency', 'weight', 1, true),
    ('stage4-weight-confidence', 'weight', 1, true),
    ('stage4-weight-density', 'weight', 1, true);

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 2, 2 FROM common.rule_definitions WHERE name = 'stage4-min-domains-early-convergence';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 3, 3 FROM common.rule_definitions WHERE name = 'stage4-min-domains-multidomain';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 75, 75 FROM common.rule_definitions WHERE name = 'stage4-strong-convergence-score-threshold';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 55, 55 FROM common.rule_definitions WHERE name = 'stage4-multidomain-score-threshold';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 180, 180 FROM common.rule_definitions WHERE name = 'stage4-convergence-max-span-days';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 30, 30 FROM common.rule_definitions WHERE name = 'stage4-market-max-age-days';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 30, 30 FROM common.rule_definitions WHERE name = 'stage4-sector-max-age-days';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 150, 150 FROM common.rule_definitions WHERE name = 'stage4-financial-max-age-days';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 150, 150 FROM common.rule_definitions WHERE name = 'stage4-ownership-max-age-days';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 180, 180 FROM common.rule_definitions WHERE name = 'stage4-capital-max-age-days';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 30, 30 FROM common.rule_definitions WHERE name = 'stage4-max-contradiction-penalty';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 90, 90 FROM common.rule_definitions WHERE name = 'stage4-risk-contradiction-max-age-days';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 30, 30 FROM common.rule_definitions WHERE name = 'stage4-weight-breadth';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 25, 25 FROM common.rule_definitions WHERE name = 'stage4-weight-maturity';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 20, 20 FROM common.rule_definitions WHERE name = 'stage4-weight-recency';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 15, 15 FROM common.rule_definitions WHERE name = 'stage4-weight-confidence';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 10, 10 FROM common.rule_definitions WHERE name = 'stage4-weight-density';
