-- Module 2.4: default rule set for the Order Book Engine (corporate.orderbook.OrderBookEngine).
-- Same pattern as V3-V7 (technical-/fundamental-/institutional-/sector-/risk- prefixes):
-- distinguished by an "orderbook-" name prefix, filtered by
-- corporate.orderbook.OrderBookRuleSetLoader. Unlike Risk's 4 categories, this is a single overall
-- Order Quality score (matching the roadmap's single "Order Quality: Excellent" dashboard output),
-- so there is no per-category averaging - all 3 rules feed one score directly.
--
-- Weights are chosen so the best-case raw sum is exactly 3.0 (score 80, the EXCELLENT threshold)
-- and the worst-case sum is -3.0 (score 20, the POOR threshold) - same convention as every prior
-- rule set, so every input combination can reach every quality band.
--
-- orderBookGrowthPct is absent from the metric context entirely on an instrument's first-ever
-- snapshot (no prior snapshot to compare against) - ArithmeticRuleEvaluator already treats a
-- missing target metric as a non-match (0 contribution), so this rule simply doesn't contribute
-- until a second snapshot exists; it is not a fabricated 0% growth value.

INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('orderbook-growth', 'orderBookGrowthPct', 1, true),
    ('orderbook-execution-visibility', 'executionVisibilityYears', 1, true),
    ('orderbook-order-count', 'orderCount', 1, true);

-- Growth: >20% period-over-period growth is a strong signal; a shrinking order book (<0%) is a
-- real concern about future revenue.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 20, 1.5 FROM common.rule_definitions WHERE name = 'orderbook-growth';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 0, -1.5 FROM common.rule_definitions WHERE name = 'orderbook-growth';

-- Execution visibility: >2 years of booked execution ahead is strong revenue visibility; <0.5
-- years means most of the order book must be replaced almost immediately.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 2, 1.0 FROM common.rule_definitions WHERE name = 'orderbook-execution-visibility';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 0.5, -1.0 FROM common.rule_definitions WHERE name = 'orderbook-execution-visibility';

-- Order count: a diversification proxy - 3+ distinct active orders means no single customer/order
-- cancellation can empty the order book; 1 or fewer is genuine concentration risk.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 3, 0.5 FROM common.rule_definitions WHERE name = 'orderbook-order-count';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', 1, -0.5 FROM common.rule_definitions WHERE name = 'orderbook-order-count';
