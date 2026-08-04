-- Module 2.6: default rule set for the News Catalyst Engine (corporate.news.NewsCatalystEngine).
-- Same pattern as V3-V9: distinguished by a "news-catalyst-" name prefix. Single overall Catalyst
-- Score (like Order Book's Order Quality, Management Commentary's Growth Visibility).
--
-- Unlike Management Commentary's quarterly-cadence guidance, news catalysts are episodic - there's
-- no natural "persistence" analogue. The three signals instead are: net direction across an
-- instrument's full recent-link history, how many independent catalysts reinforce that direction
-- (volume), and how fresh the most recent one is (recency) - a catalyst from 6 months ago is far
-- less thesis-relevant than one from yesterday.
--
-- Weights chosen so the best-case raw sum is exactly 3.0 (score 80) and the worst-case is -3.0
-- (score 20), matching every prior rule set's convention.

INSERT INTO common.rule_definitions (name, target_metric, version, active)
VALUES
    ('news-catalyst-direction', 'newsCatalystNetDirection', 1, true),
    ('news-catalyst-volume', 'newsCatalystVolume', 1, true),
    ('news-catalyst-recency', 'newsCatalystRecencyDays', 1, true);

-- Direction: a net of 2+ positive-minus-negative links (or the reverse) is a genuine directional
-- signal, not noise from one stray mention.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 2, 1.5 FROM common.rule_definitions WHERE name = 'news-catalyst-direction';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', -2, -1.5 FROM common.rule_definitions WHERE name = 'news-catalyst-direction';

-- Volume: 3+ independent catalyst links reinforcing the same instrument is more credible than a
-- single isolated mention (1 or fewer).
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 3, 1.0 FROM common.rule_definitions WHERE name = 'news-catalyst-volume';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', 1, -1.0 FROM common.rule_definitions WHERE name = 'news-catalyst-volume';

-- Recency: the target metric is DAYS since the most recent catalyst link, so the polarity is
-- inverted from direction/volume - a SMALL value (fresh, <=7 days) is the positive signal, a
-- LARGE value (stale, >=30 days) is the negative one.
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'LTE', 7, 0.5 FROM common.rule_definitions WHERE name = 'news-catalyst-recency';
INSERT INTO common.rule_conditions (rule_id, operator, threshold, weight)
SELECT id, 'GTE', 30, -0.5 FROM common.rule_definitions WHERE name = 'news-catalyst-recency';
