-- Sprint 2 of the bulk/block deal auto-discovery roadmap: default rule set for the Deal
-- Materiality Engine (ownership.deals.DealMaterialityEngine). Two-pass evaluation, same shape as
-- V13's decision-scoring rules: three ArithmeticRuleEvaluator sub-score rules (Pass 1) feed a
-- WeightedAverageRuleEvaluator blend (Pass 2), all via the existing common.rules engine - no new
-- rule-evaluation Java needed.
--
-- Pass 1a - deal-materiality-adtv-ratio (target: dealToAdtvRatio = one deal's value / the
-- symbol's 20-trading-day ADTV). A cumulative ladder, not five independent mutually-exclusive
-- BETWEEN bands: an exact boundary value (e.g. ratio == 1.00) would otherwise double-match two
-- adjacent inclusive BETWEEN conditions at once (confirmed by re-reading
-- common.rules.RuleCondition#matches - BETWEEN is inclusive on both ends). Instead, one ALWAYS
-- base condition plus four GTE step-ups sum to exactly one of the five intended scores as the
-- ratio crosses each threshold, with each band closed on its lower bound / open on its upper
-- bound - [0, 0.10)=10 (VERY_LOW), [0.10, 0.25)=30 (LOW), [0.25, 0.50)=55 (MEDIUM),
-- [0.50, 1.00)=80 (HIGH), [1.00, inf)=100 (VERY_HIGH). This exactly reproduces the RSI-momentum
-- precedent in V3 (a BETWEEN band flanked by exclusive-GT/LT bands), generalized to more tiers.
--
-- Pass 1b/1c - deal-materiality-repetition / deal-materiality-breadth (targets:
-- sameSideClientDealCount20CalendarDays / distinctSameSideClients20CalendarDays - resolved by the
-- deal's own direction in Java, direction-neutral by design, see DealMaterialityEngine). Both
-- integer counts, so exact EQ matches are safe (no continuous-boundary ambiguity): 1->20, 2->50,
-- 3->75, >=4->100.
--
-- Pass 2 - deal-materiality-blend-* (WeightedAverageRuleEvaluator, ALWAYS, matching V13's exact
-- shape: one rule_definitions row per blended input, sharing a name prefix the engine filters on,
-- weight = blend coefficient applied directly to the already-0-100 sub-score). 70% ADTV ratio /
-- 20% repetition / 10% breadth - ratio dominates because it's the only input that scales with how
-- much money actually moved; repetition/breadth are corroborating, not primary, signals.

INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('deal-materiality-adtv-ratio', 'dealToAdtvRatio', 1, true),
    ('deal-materiality-repetition', 'sameSideClientDealCount20CalendarDays', 1, true),
    ('deal-materiality-breadth', 'distinctSameSideClients20CalendarDays', 1, true),
    ('deal-materiality-blend-adtv-ratio', 'materialityAdtvRatioScore', 1, true),
    ('deal-materiality-blend-repetition', 'materialityRepetitionScore', 1, true),
    ('deal-materiality-blend-breadth', 'materialityBreadthScore', 1, true);

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 10 FROM common.rule_definitions WHERE name = 'deal-materiality-adtv-ratio';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 0.10, 20 FROM common.rule_definitions WHERE name = 'deal-materiality-adtv-ratio';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 0.25, 25 FROM common.rule_definitions WHERE name = 'deal-materiality-adtv-ratio';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 0.50, 25 FROM common.rule_definitions WHERE name = 'deal-materiality-adtv-ratio';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 1.00, 20 FROM common.rule_definitions WHERE name = 'deal-materiality-adtv-ratio';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'EQ', 1, 20 FROM common.rule_definitions WHERE name = 'deal-materiality-repetition';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'EQ', 2, 50 FROM common.rule_definitions WHERE name = 'deal-materiality-repetition';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'EQ', 3, 75 FROM common.rule_definitions WHERE name = 'deal-materiality-repetition';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 4, 100 FROM common.rule_definitions WHERE name = 'deal-materiality-repetition';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'EQ', 1, 20 FROM common.rule_definitions WHERE name = 'deal-materiality-breadth';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'EQ', 2, 50 FROM common.rule_definitions WHERE name = 'deal-materiality-breadth';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'EQ', 3, 75 FROM common.rule_definitions WHERE name = 'deal-materiality-breadth';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 4, 100 FROM common.rule_definitions WHERE name = 'deal-materiality-breadth';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.70 FROM common.rule_definitions WHERE name = 'deal-materiality-blend-adtv-ratio';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.20 FROM common.rule_definitions WHERE name = 'deal-materiality-blend-repetition';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'ALWAYS', 0, 0.10 FROM common.rule_definitions WHERE name = 'deal-materiality-blend-breadth';
