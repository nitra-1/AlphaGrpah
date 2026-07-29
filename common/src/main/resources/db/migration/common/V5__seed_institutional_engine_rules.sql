-- Module 1.7: default rule set for the Institutional Engine (ownership.engine.InstitutionalEngine).
-- Same pattern as V3 (technical-*) and V4 (fundamental-*): distinguished by an "institutional-"
-- name prefix, filtered by ownership.engine.InstitutionalRuleSetLoader. Replaces hard-coded
-- thresholds per docs/002_Engine_Architecture.md §4/§7 - editable later via the existing
-- rule-definitions CRUD API without a redeploy. Weights and the raw-score-to-0-100 mapping are an
-- engine design choice documented in InstitutionalEngine itself.

INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('institutional-promoter-trend', 'promoterChangePct', 1, true),
    ('institutional-fii-trend', 'fiiChangePct', 1, true),
    ('institutional-dii-trend', 'diiChangePct', 1, true),
    ('institutional-mf-trend', 'mfChangePct', 1, true),
    ('institutional-delivery-high', 'avgDeliveryPercentage', 1, true),
    ('institutional-volume-expansion', 'relativeVolume', 1, true),
    ('institutional-bulk-deal-flow', 'netBulkDealQuantity', 1, true);

-- Promoter/FII/DII/MF: a change of more than +/- 0.5 percentage points between shareholding
-- periods is treated as a real accumulation/distribution signal, not noise from rounding.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 0.5, 1.0 FROM common.rule_definitions WHERE name = 'institutional-promoter-trend';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', -0.5, -1.0 FROM common.rule_definitions WHERE name = 'institutional-promoter-trend';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 0.5, 1.0 FROM common.rule_definitions WHERE name = 'institutional-fii-trend';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', -0.5, -1.0 FROM common.rule_definitions WHERE name = 'institutional-fii-trend';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 0.5, 1.0 FROM common.rule_definitions WHERE name = 'institutional-dii-trend';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', -0.5, -1.0 FROM common.rule_definitions WHERE name = 'institutional-dii-trend';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 0.5, 1.0 FROM common.rule_definitions WHERE name = 'institutional-mf-trend';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', -0.5, -1.0 FROM common.rule_definitions WHERE name = 'institutional-mf-trend';

-- Delivery: >70% average delivery is a strong conviction signal, 50-70% is still healthy.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 70, 1.0 FROM common.rule_definitions WHERE name = 'institutional-delivery-high';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, upper_bound, weight)
SELECT id, 'BETWEEN', 50, 70, 0.5 FROM common.rule_definitions WHERE name = 'institutional-delivery-high';

-- Volume: >=1.5x the recent average volume signals real institutional-size participation.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 1.5, 1.0 FROM common.rule_definitions WHERE name = 'institutional-volume-expansion';

-- Bulk/block deals: net buy quantity positive means more real bulk/block buying than selling.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 0, 1.0 FROM common.rule_definitions WHERE name = 'institutional-bulk-deal-flow';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 0, -1.0 FROM common.rule_definitions WHERE name = 'institutional-bulk-deal-flow';
