-- Module 2.8: Corporate Signal Engine. Combines the four already-built corporate engines'
-- outputs (Order Book quality, Management growth visibility, News catalyst strength, and
-- Corporate Event signal) into one Corporate Score - the roadmap's own worked example ("Order Win
-- + Positive Guidance + Capacity Expansion + Strong Demand = Corporate Score") names one ingredient
-- from each of the four. Deliberately narrower than Phase 1's originally-planned (never built)
-- Scoring/Decision Engine, which would also blend in Technical/Fundamental/Institutional/Sector/
-- Risk - that remains separate, not-yet-requested future work; this module only combines
-- corporate-domain signals, same domain boundary every engine in this schema already respects.
--
-- Contributing domain scores are stored alongside the composite (order_book_score/
-- management_score/news_catalyst_score/event_signal_count) even though nothing downstream reads
-- them yet - Module 2.9's AI Analyst is explicitly designed to explain, not calculate, and
-- explaining "why did the Corporate Score improve" needs to know which domain moved, not just the
-- final number.
CREATE TABLE corporate.corporate_scores (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id         uuid NOT NULL,
    symbol                varchar(20) NOT NULL,
    as_of_date            date NOT NULL,
    corporate_score       numeric(5,2) NOT NULL,
    corporate_rating      varchar(10) NOT NULL,
    order_book_score      numeric(5,2),
    management_score      numeric(5,2),
    news_catalyst_score   numeric(5,2),
    event_net_signal      integer NOT NULL,
    confidence            numeric(5,2) NOT NULL,
    rule_set_version      integer NOT NULL,
    computed_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_corporate_scores_rating CHECK (corporate_rating IN ('EXCELLENT', 'STRONG', 'NEUTRAL', 'WEAK', 'POOR')),
    CONSTRAINT ux_corporate_scores_instrument_date UNIQUE (instrument_id, as_of_date)
);

CREATE INDEX ix_corporate_scores_instrument_id ON corporate.corporate_scores (instrument_id);
