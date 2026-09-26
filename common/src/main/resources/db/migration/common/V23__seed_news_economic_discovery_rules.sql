-- News & Economic Discovery: expiry (half-life v1 - a simple fixed cutoff per event magnitude,
-- not a continuously-recomputed decay curve, disclosed simplification). Same named-scalar-
-- constant convention as V16-V22's Stage 3/4/5 rules - each rule has exactly one ALWAYS condition;
-- Java reads its `threshold` back directly. Structural logic (company-resolution match-type
-- ordering, decision-hierarchy) stays as Java constants in corporate.news.CompanyResolver/
-- EconomicEventWriter, same tradeoff every prior stage's own engine already made.
INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('news-expiry-days-high', 'expiryDays', 1, true),
    ('news-expiry-days-medium', 'expiryDays', 1, true),
    ('news-expiry-days-low', 'expiryDays', 1, true);

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 90, 90 FROM common.rule_definitions WHERE name = 'news-expiry-days-high';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 30, 30 FROM common.rule_definitions WHERE name = 'news-expiry-days-medium';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 14, 14 FROM common.rule_definitions WHERE name = 'news-expiry-days-low';
