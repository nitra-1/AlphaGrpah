-- Module 2.8: default rule set for the Corporate Signal Engine (corporate.signal.CorporateSignalEngine).
-- Combines the four already-built corporate engines' outputs into one Corporate Score - the
-- roadmap's own worked example ("Order Win + Positive Guidance + Capacity Expansion + Strong
-- Demand = Corporate Score") names one ingredient from each domain.
--
-- Order Book and Management weighted equally highest (the two richest, most-frequently-updated
-- domains); Corporate Events next (concrete/factual but more binary - either an event happened or
-- it didn't); News Catalyst lowest (most episodic/noisy, per its own engine's design commentary).
-- Best-case raw sum is exactly 3.0 (score 80), worst-case -3.0 (score 20), matching every prior
-- rule set's convention.

INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('corporate-orderbook-strength', 'corporateOrderBookScore', 1, true),
    ('corporate-management-strength', 'corporateManagementScore', 1, true),
    ('corporate-news-catalyst-strength', 'corporateNewsCatalystScore', 1, true),
    ('corporate-event-signal', 'corporateEventNetSignal', 1, true);

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 70, 0.75 FROM common.rule_definitions WHERE name = 'corporate-orderbook-strength';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', 30, -0.75 FROM common.rule_definitions WHERE name = 'corporate-orderbook-strength';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 70, 0.75 FROM common.rule_definitions WHERE name = 'corporate-management-strength';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', 30, -0.75 FROM common.rule_definitions WHERE name = 'corporate-management-strength';

INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 70, 0.5 FROM common.rule_definitions WHERE name = 'corporate-news-catalyst-strength';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', 30, -0.5 FROM common.rule_definitions WHERE name = 'corporate-news-catalyst-strength';

-- Net signal is (count of POSITIVE-signal events) - (count of NEGATIVE-signal events) among an
-- instrument's recent corporate events (see corporate.signal.CorporateSignalOrchestrator).
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 2, 1.0 FROM common.rule_definitions WHERE name = 'corporate-event-signal';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', -1, -1.0 FROM common.rule_definitions WHERE name = 'corporate-event-signal';
