-- Module 3.7 caching retrofit: AI Analyst explanations (score-explanation, rank-explanation) were
-- a real, uncached Claude API call on every single GET - deliberate at the time ("naturally
-- lower-traffic than every other GET endpoint by design"), but that assumption breaks down once
-- many users can ask the same question about the same instrument. Cached per
-- (instrument_id, explanation_type, business_date): the underlying facts (decision/corporate
-- scores) only change on a scheduled recompute, at most once per day, so every user asking "why
-- this Corporate Score / Swing Rank" for the same instrument on the same day gets back the same
-- already-generated explanation instead of a fresh billed Claude call. Mirrors
-- decision.daily_reports' own once-per-day convention (Module 3.6).
CREATE TABLE decision.analyst_explanations (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id     uuid NOT NULL,
    explanation_type  varchar(20) NOT NULL,
    business_date     date NOT NULL,
    explanation       text NOT NULL,
    generated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_analyst_explanations_key UNIQUE (instrument_id, explanation_type, business_date)
);

CREATE INDEX ix_analyst_explanations_lookup ON decision.analyst_explanations (instrument_id, explanation_type, business_date);
