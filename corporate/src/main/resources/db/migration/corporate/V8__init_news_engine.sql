-- Module 2.6: News Intelligence Engine. Unlike every prior source (exchange filings, earnings
-- calls), a news item genuinely belongs to zero, one, or many companies - "Government announces
-- Semiconductor PLI" isn't filed BY a company, it AFFECTS several. That breaks the assumption
-- baked into every source since Module 2.1: one document, one instrument_id, known at collection
-- time. instrument_id/symbol become nullable (NULL only for NEWS-sourced rows collected before
-- any AI company-linking has run) - every other source keeps them effectively required by
-- convention, just no longer enforced at the column level for a source that genuinely can't know
-- them yet.
ALTER TABLE corporate.documents ALTER COLUMN instrument_id DROP NOT NULL;
ALTER TABLE corporate.documents ALTER COLUMN symbol DROP NOT NULL;

-- NSE's seq_id (the only external_id source until now) is a short numeric string, comfortably
-- under varchar(50). RSS GUIDs are full article URLs - often 150-300+ characters. Widened rather
-- than hashed so the natural source identifier stays human-readable for debugging (a hash would
-- obscure it for no real benefit - external_id was never meant to be short, just stable).
ALTER TABLE corporate.documents ALTER COLUMN external_id TYPE varchar(500);

-- Layer 1: one immutable row per (news document, affected company) - written by
-- corporate.news.NewsLinkParser after grouping corporate.knowledge.NewsExtractor's facts by
-- fact_group (same pattern as management_observations, Module 2.5). Only tracked instruments get
-- a row here - NewsExtractor itself doesn't know AlphaGraph's tracked universe (it names
-- companies freely, in its own words, from the text), and corporate.news.NewsInstrumentMatcher
-- resolves each name against reference.instruments deterministically; an unresolved name (a real,
-- common case - most news mentions companies AlphaGraph doesn't track yet) simply produces no
-- link, not a row with a null instrument. This is the concrete implementation of the "tracked
-- instruments only" scoping decision.
CREATE TABLE corporate.document_instrument_links (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id             uuid NOT NULL REFERENCES corporate.documents (id) ON DELETE CASCADE,
    instrument_id           uuid NOT NULL,
    symbol                  varchar(20) NOT NULL,
    direction               varchar(10) NOT NULL,
    signal                  varchar(100) NOT NULL,
    impact_summary          text NOT NULL,
    extraction_confidence   numeric(5,2) NOT NULL,
    announced_at            timestamptz NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_document_instrument_links_direction CHECK (direction IN ('POSITIVE', 'NEGATIVE', 'NEUTRAL'))
);

CREATE INDEX ix_document_instrument_links_document_id ON corporate.document_instrument_links (document_id);
CREATE INDEX ix_document_instrument_links_instrument_id ON corporate.document_instrument_links (instrument_id);

-- Layer 2: corporate.news.NewsCatalystEngine's point-in-time output, one row per (instrument,
-- day) - same shape as order_book_snapshots/management_commentary_snapshots.
CREATE TABLE corporate.news_catalyst_scores (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id           uuid NOT NULL,
    symbol                  varchar(20) NOT NULL,
    as_of_date              date NOT NULL,
    catalyst_score          numeric(5,2) NOT NULL,
    catalyst_trend          varchar(10) NOT NULL,
    recent_catalyst_count   integer NOT NULL,
    confidence              numeric(5,2) NOT NULL,
    rule_set_version        integer NOT NULL,
    computed_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_news_catalyst_scores_trend CHECK (catalyst_trend IN ('POSITIVE', 'NEGATIVE', 'MIXED', 'NONE')),
    CONSTRAINT ux_news_catalyst_scores_instrument_date UNIQUE (instrument_id, as_of_date)
);

CREATE INDEX ix_news_catalyst_scores_instrument_id ON corporate.news_catalyst_scores (instrument_id);
