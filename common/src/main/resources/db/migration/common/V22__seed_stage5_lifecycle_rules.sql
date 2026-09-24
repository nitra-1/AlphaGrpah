-- Stage 5 Lifecycle Classification thresholds (docs/008_Stage3_Sequence_Detection_Specification.md
-- hand-off chain, extended by Stage 4 then Stage 5 - the user's own detailed Stage 5 spec): same
-- named-scalar-constant convention as V16-V21's Stage 3/4 rules - each rule has exactly one ALWAYS
-- condition; Java reads its `threshold` back directly, never runs it through
-- ArithmeticRuleEvaluator's scoring.
--
-- Structural/piecewise logic (DETERIORATING's "2 of 5 dimensions", which states get hysteresis
-- pairs, decision-hierarchy evaluation order, the cycle-peak rank mapping) stays as Java constants
-- in DiscoveryLifecycleEngine, same tradeoff Stage 4 already made for its own band tables - only
-- genuine scalar thresholds/weights are rules here.
--
-- stage5-market-recognition-min-market-contribution and the 4 stage5-weight-* rules were added
-- during plan review (rounds 3-4), beyond the spec's own original 18 named rules - a real,
-- previously-unnamed threshold made explicit/versioned, and lifecycle-strength composition
-- weights externalized the same way Stage 4 externalizes its own 5 scoring weights.
INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('stage5-min-ready-observations', 'minReadyObservations', 1, true),
    ('stage5-min-history-span-days', 'minHistorySpanDays', 1, true),
    ('stage5-short-window-observations', 'shortWindowObservations', 1, true),
    ('stage5-medium-window-observations', 'mediumWindowObservations', 1, true),
    ('stage5-long-window-observations', 'longWindowObservations', 1, true),
    ('stage5-early-inflection-min-persistence', 'minPersistence', 1, true),
    ('stage5-emerging-min-persistence', 'minPersistence', 1, true),
    ('stage5-accelerating-min-persistence', 'minPersistence', 1, true),
    ('stage5-market-recognition-min-persistence', 'minPersistence', 1, true),
    ('stage5-mature-rerating-min-days', 'minDurationDays', 1, true),
    ('stage5-deterioration-min-persistence', 'minPersistence', 1, true),
    ('stage5-market-recognition-min-market-contribution', 'minMarketContribution', 1, true),
    ('stage5-rising-score-delta', 'scoreDelta', 1, true),
    ('stage5-weakening-score-delta', 'scoreDelta', 1, true),
    ('stage5-breadth-expansion-min-delta', 'domainDelta', 1, true),
    ('stage5-breadth-contraction-min-delta', 'domainDelta', 1, true),
    ('stage5-acceleration-entry-threshold', 'trajectoryScoreThreshold', 1, true),
    ('stage5-acceleration-exit-threshold', 'trajectoryScoreThreshold', 1, true),
    ('stage5-dormant-min-observations', 'minObservations', 1, true),
    ('stage5-weight-persistence', 'weight', 1, true),
    ('stage5-weight-evidence-confidence', 'weight', 1, true),
    ('stage5-weight-trajectory-consistency', 'weight', 1, true),
    ('stage5-weight-history-quality', 'weight', 1, true);

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 10, 10 FROM common.rule_definitions WHERE name = 'stage5-min-ready-observations';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 14, 14 FROM common.rule_definitions WHERE name = 'stage5-min-history-span-days';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 5, 5 FROM common.rule_definitions WHERE name = 'stage5-short-window-observations';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 10, 10 FROM common.rule_definitions WHERE name = 'stage5-medium-window-observations';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 20, 20 FROM common.rule_definitions WHERE name = 'stage5-long-window-observations';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 2, 2 FROM common.rule_definitions WHERE name = 'stage5-early-inflection-min-persistence';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 3, 3 FROM common.rule_definitions WHERE name = 'stage5-emerging-min-persistence';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 3, 3 FROM common.rule_definitions WHERE name = 'stage5-accelerating-min-persistence';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 3, 3 FROM common.rule_definitions WHERE name = 'stage5-market-recognition-min-persistence';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 60, 60 FROM common.rule_definitions WHERE name = 'stage5-mature-rerating-min-days';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 3, 3 FROM common.rule_definitions WHERE name = 'stage5-deterioration-min-persistence';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 60, 60 FROM common.rule_definitions WHERE name = 'stage5-market-recognition-min-market-contribution';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 8, 8 FROM common.rule_definitions WHERE name = 'stage5-rising-score-delta';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', -8, -8 FROM common.rule_definitions WHERE name = 'stage5-weakening-score-delta';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 1, 1 FROM common.rule_definitions WHERE name = 'stage5-breadth-expansion-min-delta';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', -1, -1 FROM common.rule_definitions WHERE name = 'stage5-breadth-contraction-min-delta';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 65, 65 FROM common.rule_definitions WHERE name = 'stage5-acceleration-entry-threshold';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 55, 55 FROM common.rule_definitions WHERE name = 'stage5-acceleration-exit-threshold';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 10, 10 FROM common.rule_definitions WHERE name = 'stage5-dormant-min-observations';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 35, 35 FROM common.rule_definitions WHERE name = 'stage5-weight-persistence';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 25, 25 FROM common.rule_definitions WHERE name = 'stage5-weight-evidence-confidence';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 20, 20 FROM common.rule_definitions WHERE name = 'stage5-weight-trajectory-consistency';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 20, 20 FROM common.rule_definitions WHERE name = 'stage5-weight-history-quality';
