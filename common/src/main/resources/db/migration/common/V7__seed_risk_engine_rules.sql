-- Module 1.9: default rule set for the Risk Engine (risk.engine.RiskEngine). Same pattern as
-- V3 (technical-*), V4 (fundamental-*), V5 (institutional-*), V6 (sector-*): distinguished by a
-- "risk-" name prefix, filtered by risk.engine.RiskRuleSetLoader. Unlike every prior rule set,
-- weights here are BIDIRECTIONAL by design - a positive weight marks a genuine safety signal, a
-- negative weight marks a genuine risk signal - so the resulting 0-100 score can be pulled in
-- either direction rather than only ever being discounted from a ceiling. This needed no new
-- evaluator: common.rules.ArithmeticRuleEvaluator already just sums whatever weight each matched
-- condition carries, negative or positive.
--
-- 13 rules across 4 categories, further prefixed risk-business-/risk-technical-/
-- risk-ownership-/risk-valuation- so risk.engine.RiskEngine can score each category
-- independently (raw score -> 50 + raw*10, clamped 0-100) before averaging them into an overall
-- score. Weights within each category are chosen so the category's own best-case raw sum is
-- exactly 3.0 (score 80, the VERY_LOW threshold) and its worst-case sum is at or beyond -3.0
-- (score <=20, the VERY_HIGH threshold) - every category can reach every band, not just some,
-- regardless of how many signals feed it (Technical and Valuation have fewer inputs than
-- Business and Ownership, so their per-signal weights run proportionally higher).
--
-- This covers 13 of the 16 risk factors named in the roadmap; the remaining 3 ("Lower highs" and
-- "Distribution" as literal chart patterns distinct from momentum weakening, and all of "Event
-- Risk") are not modeled as separate metrics because no existing engine output distinguishes
-- them from what's already covered here - see RiskEngine's javadoc for exactly which named
-- factor each rule stands in for.

INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('risk-business-revenue-growth', 'revenueGrowthPct', 1, true),
    ('risk-business-profitability-trend', 'profitabilityTrend', 1, true),
    ('risk-business-leverage', 'debtToEquity', 1, true),
    ('risk-business-cash-conversion', 'cashConversionRatio', 1, true),
    ('risk-technical-trend', 'technicalTrendSignal', 1, true),
    ('risk-technical-momentum', 'momentumSignal', 1, true),
    ('risk-technical-volume', 'volumeSignal', 1, true),
    ('risk-ownership-promoter', 'promoterTrendSignal', 1, true),
    ('risk-ownership-fii', 'fiiTrendSignal', 1, true),
    ('risk-ownership-mf', 'mfTrendSignal', 1, true),
    ('risk-ownership-delivery', 'deliverySignal', 1, true),
    ('risk-valuation-pe', 'peRatio', 1, true),
    ('risk-valuation-pb', 'pbRatio', 1, true);

-- Business Risk (4 signals, max +3.0 / min -4.0)

-- Revenue decline: >10% YoY growth is a real safety signal, <0% (an actual decline) is real risk.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 10, 1.0 FROM common.rule_definitions WHERE name = 'risk-business-revenue-growth';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 0, -1.0 FROM common.rule_definitions WHERE name = 'risk-business-revenue-growth';

-- Margin decline: signed +1/0/-1 from financial.Profitability (IMPROVING/STABLE/DECLINING).
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 1, 1.0 FROM common.rule_definitions WHERE name = 'risk-business-profitability-trend';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', -1, -1.0 FROM common.rule_definitions WHERE name = 'risk-business-profitability-trend';

-- High debt: debt-to-equity <0.5 is conservatively low leverage, >1.5 is a real solvency concern.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 0.5, 0.5 FROM common.rule_definitions WHERE name = 'risk-business-leverage';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 1.5, -1.0 FROM common.rule_definitions WHERE name = 'risk-business-leverage';

-- Negative cash flow (proxy): cash_conversion_ratio >1.0 means operating cash comfortably covers
-- PAT; <0 means the ratio itself is negative - a real red flag, not just weak.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 1.0, 0.5 FROM common.rule_definitions WHERE name = 'risk-business-cash-conversion';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 0, -1.0 FROM common.rule_definitions WHERE name = 'risk-business-cash-conversion';

-- Technical Risk (3 signals, max +3.0 / min -3.5)

-- Trend reversal: signed +1/0/-1 from technical.Trend, STRONG_* collapsed into their plain counterpart.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 1, 1.5 FROM common.rule_definitions WHERE name = 'risk-technical-trend';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', -1, -1.5 FROM common.rule_definitions WHERE name = 'risk-technical-trend';

-- Distribution / lower highs (proxy): signed +1/0/-1 from technical.Momentum - weakening momentum
-- into a rally is the shared real signature of both named chart patterns.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 1, 1.0 FROM common.rule_definitions WHERE name = 'risk-technical-momentum';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', -1, -1.0 FROM common.rule_definitions WHERE name = 'risk-technical-momentum';

-- Weak volume: signed +1/0/-1 from technical.VolumeState.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 1, 0.5 FROM common.rule_definitions WHERE name = 'risk-technical-volume';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', -1, -1.0 FROM common.rule_definitions WHERE name = 'risk-technical-volume';

-- Ownership Risk (4 signals, max +3.0 / min -4.5)

-- Promoter selling: signed +1/0/-1 from ownership.PromoterStatus.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 1, 1.5 FROM common.rule_definitions WHERE name = 'risk-ownership-promoter';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', -1, -1.5 FROM common.rule_definitions WHERE name = 'risk-ownership-promoter';

-- FII exit: signed +1/0/-1 from ownership.InstitutionalFlowStatus.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 1, 0.5 FROM common.rule_definitions WHERE name = 'risk-ownership-fii';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', -1, -1.0 FROM common.rule_definitions WHERE name = 'risk-ownership-fii';

-- MF exit: signed +1/0/-1 from ownership.InstitutionalFlowStatus.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 1, 0.5 FROM common.rule_definitions WHERE name = 'risk-ownership-mf';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', -1, -1.0 FROM common.rule_definitions WHERE name = 'risk-ownership-mf';

-- Low delivery: signed +1/0/-1 from ownership.DeliveryStatus (VERY_HIGH/HIGH collapsed together).
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 1, 0.5 FROM common.rule_definitions WHERE name = 'risk-ownership-delivery';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', -1, -1.0 FROM common.rule_definitions WHERE name = 'risk-ownership-delivery';

-- Valuation Risk (2 signals, max +3.0 / min -3.0)

-- High PE / very expensive: PE derived as latestClose / eps (both real, already-sourced figures).
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 15, 1.5 FROM common.rule_definitions WHERE name = 'risk-valuation-pe';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 30, -1.5 FROM common.rule_definitions WHERE name = 'risk-valuation-pe';

-- High PB / overextended: PB derived from book value per share = totalEquity * eps / pat
-- (implied shares outstanding = pat / eps) - see RiskEngine javadoc for the full derivation.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LT', 2, 1.5 FROM common.rule_definitions WHERE name = 'risk-valuation-pb';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GT', 5, -1.5 FROM common.rule_definitions WHERE name = 'risk-valuation-pb';
