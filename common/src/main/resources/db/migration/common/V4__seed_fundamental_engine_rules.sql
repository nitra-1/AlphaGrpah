-- Module 1.6: default rule set for the Fundamental Engine (financial.engine.FundamentalEngine).
-- Same pattern as V3's technical-* rules: distinguished purely by a "fundamental-" name prefix
-- (common.rule_definitions has no module-scoping column), filtered by
-- financial.engine.RuleSetLoader. Replaces what would otherwise be hard-coded thresholds per
-- docs/002_Engine_Architecture.md §4/§7 - editable later via the existing rule-definitions CRUD
-- API without a redeploy. Weights and the raw-score-to-0-100 mapping are an engine design choice
-- documented in FundamentalEngine itself, not something this seed data claims to be an
-- authoritative industry-standard formula.

INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('fundamental-revenue-growth', 'revenueGrowthPct', 1, true),
    ('fundamental-pat-growth', 'patGrowthPct', 1, true),
    ('fundamental-roe-quality', 'roePercentage', 1, true),
    ('fundamental-roce-quality', 'rocePercentage', 1, true),
    ('fundamental-net-margin-quality', 'netMarginPercentage', 1, true),
    ('fundamental-asset-turnover-efficiency', 'assetTurnover', 1, true),
    ('fundamental-interest-coverage-leverage', 'interestCoverage', 1, true),
    ('fundamental-debt-equity-leverage', 'debtToEquity', 1, true);

-- Growth: >15% YoY is strong, 0-15% is still positive but unremarkable, negative is a red flag.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 15, 1.0 FROM common.rule_definitions WHERE name = 'fundamental-revenue-growth';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, upper_bound, weight)
SELECT id, 'BETWEEN', 0, 15, 0.5 FROM common.rule_definitions WHERE name = 'fundamental-revenue-growth';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 0, -1.0 FROM common.rule_definitions WHERE name = 'fundamental-revenue-growth';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 15, 1.0 FROM common.rule_definitions WHERE name = 'fundamental-pat-growth';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, upper_bound, weight)
SELECT id, 'BETWEEN', 0, 15, 0.5 FROM common.rule_definitions WHERE name = 'fundamental-pat-growth';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 0, -1.0 FROM common.rule_definitions WHERE name = 'fundamental-pat-growth';

-- Quality: ROE/ROCE above 20% is high quality, 10-20% is acceptable, below 10% is weak.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 20, 1.0 FROM common.rule_definitions WHERE name = 'fundamental-roe-quality';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, upper_bound, weight)
SELECT id, 'BETWEEN', 10, 20, 0.5 FROM common.rule_definitions WHERE name = 'fundamental-roe-quality';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 10, -0.5 FROM common.rule_definitions WHERE name = 'fundamental-roe-quality';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 20, 1.0 FROM common.rule_definitions WHERE name = 'fundamental-roce-quality';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, upper_bound, weight)
SELECT id, 'BETWEEN', 10, 20, 0.5 FROM common.rule_definitions WHERE name = 'fundamental-roce-quality';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 10, -0.5 FROM common.rule_definitions WHERE name = 'fundamental-roce-quality';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 15, 1.0 FROM common.rule_definitions WHERE name = 'fundamental-net-margin-quality';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, upper_bound, weight)
SELECT id, 'BETWEEN', 5, 15, 0.5 FROM common.rule_definitions WHERE name = 'fundamental-net-margin-quality';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 5, -0.5 FROM common.rule_definitions WHERE name = 'fundamental-net-margin-quality';

-- Efficiency: Sales/Total Assets above 1.0x means the company generates more revenue than its
-- asset base each period - a reasonable efficiency bar for a first pass across sectors.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 1.0, 1.0 FROM common.rule_definitions WHERE name = 'fundamental-asset-turnover-efficiency';

-- Leverage: Interest Coverage (EBIT/Interest Expense) above 5x is comfortable, 2-5x is
-- serviceable, below 2x is a real risk signal.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 5, 1.0 FROM common.rule_definitions WHERE name = 'fundamental-interest-coverage-leverage';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, upper_bound, weight)
SELECT id, 'BETWEEN', 2, 5, 0.3 FROM common.rule_definitions WHERE name = 'fundamental-interest-coverage-leverage';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 2, -1.0 FROM common.rule_definitions WHERE name = 'fundamental-interest-coverage-leverage';

-- Leverage: Debt/Equity below 0.5 is conservative, 0.5-1.5 is neutral, above 1.5 is a risk flag.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 0.5, 1.0 FROM common.rule_definitions WHERE name = 'fundamental-debt-equity-leverage';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 1.5, -1.0 FROM common.rule_definitions WHERE name = 'fundamental-debt-equity-leverage';
