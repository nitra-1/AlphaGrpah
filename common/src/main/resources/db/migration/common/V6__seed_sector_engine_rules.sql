-- Module 1.8: default rule set for the Sector Engine (sector.engine.SectorEngine). Same pattern
-- as V3 (technical-*), V4 (fundamental-*), V5 (institutional-*): distinguished by a "sector-"
-- name prefix, filtered by sector.engine.SectorRuleSetLoader. Weights and the raw-score-to-0-100
-- mapping are an engine design choice documented in SectorEngine itself.

INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('sector-relative-strength', 'relativeStrength', 1, true),
    ('sector-breadth', 'breadthPct', 1, true),
    ('sector-participation', 'participationPct', 1, true),
    ('sector-volume-expansion', 'sectorVolumeRatio', 1, true),
    ('sector-performance', 'sectorPerformancePct', 1, true);

-- Relative Strength: sector 10-day return vs the whole tracked market's 10-day return, in
-- percentage points. >2pp ahead is a real outperformance signal, <-2pp is real underperformance.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 2, 1.0 FROM common.rule_definitions WHERE name = 'sector-relative-strength';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, upper_bound, weight)
SELECT id, 'BETWEEN', 0, 2, 0.5 FROM common.rule_definitions WHERE name = 'sector-relative-strength';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', -2, -1.0 FROM common.rule_definitions WHERE name = 'sector-relative-strength';

-- Breadth: % of constituents with a positive 10-day return.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 70, 1.0 FROM common.rule_definitions WHERE name = 'sector-breadth';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, upper_bound, weight)
SELECT id, 'BETWEEN', 50, 70, 0.5 FROM common.rule_definitions WHERE name = 'sector-breadth';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 30, -1.0 FROM common.rule_definitions WHERE name = 'sector-breadth';

-- Participation: % of constituents both rising AND above-average volume - a stricter,
-- volume-confirmed version of breadth.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 50, 1.0 FROM common.rule_definitions WHERE name = 'sector-participation';

-- Volume: sector's average relative volume >=1.3x its own recent baseline signals real money
-- moving, not just price drifting on thin volume.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 1.3, 1.0 FROM common.rule_definitions WHERE name = 'sector-volume-expansion';

-- Performance: the sector's own 10-day return, independent of the market-relative comparison.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 5, 1.0 FROM common.rule_definitions WHERE name = 'sector-performance';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', -5, -1.0 FROM common.rule_definitions WHERE name = 'sector-performance';
