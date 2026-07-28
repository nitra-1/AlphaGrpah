-- Module 1.5: default rule set for the Technical Engine (technical.engine.TechnicalEngine).
-- common.rule_definitions has no module-scoping column of its own (it's a shared kernel table
-- any engine can use), so these are distinguished purely by a "technical-" name prefix, which
-- technical.engine.RuleSetLoader filters on when loading the active RuleSet.
--
-- These seven rules replace what would otherwise be hard-coded thresholds (if (rsi > 60), etc.)
-- per docs/002_Engine_Architecture.md §4/§7 - editable later via the existing rule-definitions
-- CRUD API (Module 0.9) without a redeploy. Weights and the raw-score-to-0-100 mapping are an
-- engine design choice documented in TechnicalEngine itself, not something this seed data claims
-- to be an authoritative industry-standard formula.

INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('technical-price-above-sma50', 'priceVsSma50Pct', 1, true),
    ('technical-price-above-sma200', 'priceVsSma200Pct', 1, true),
    ('technical-rsi-momentum', 'rsi14', 1, true),
    ('technical-macd-bullish', 'macdHistogram', 1, true),
    ('technical-adx-trending', 'adx14', 1, true),
    ('technical-relative-volume', 'relativeVolume', 1, true),
    ('technical-obv-rising', 'obvSlope', 1, true);

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 0, 1.0 FROM common.rule_definitions WHERE name = 'technical-price-above-sma50';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 0, 1.0 FROM common.rule_definitions WHERE name = 'technical-price-above-sma200';

-- RSI: a healthy bullish zone (50-70) contributes positively; overbought (>70) and oversold (<30)
-- both contribute negatively, since either extreme signals an unstable/unsustainable move.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, upper_bound, weight)
SELECT id, 'BETWEEN', 50, 70, 1.0 FROM common.rule_definitions WHERE name = 'technical-rsi-momentum';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 70, -0.5 FROM common.rule_definitions WHERE name = 'technical-rsi-momentum';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 30, -1.0 FROM common.rule_definitions WHERE name = 'technical-rsi-momentum';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 0, 1.0 FROM common.rule_definitions WHERE name = 'technical-macd-bullish';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 25, 1.0 FROM common.rule_definitions WHERE name = 'technical-adx-trending';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 1.5, 1.0 FROM common.rule_definitions WHERE name = 'technical-relative-volume';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 0, 1.0 FROM common.rule_definitions WHERE name = 'technical-obv-rising';
