-- Module 2.3: Corporate Event Engine - the first Corporate Intelligence engine. Unlike every
-- Phase 1 engine (Technical/Fundamental/Institutional/Sector/Risk), this one classifies free text
-- into events via a real Claude API call, not deterministic threshold rules over clean numeric
-- metrics - see corporate.events.CorporateEventEngine for why it deliberately does not implement
-- common.engine.Engine<I, O extends Score> (zero-or-more events per document, not exactly one
-- score; no RuleSet involved).

-- One detected event within one document. A single document can describe zero events (a routine
-- filing that matches none of the 13 named categories - not an error, a real outcome), or more
-- than one (e.g. an investor presentation mentioning both a new order and a capacity expansion),
-- so this is a child table keyed by document_id, not a 1:1 extension of corporate.documents.
--
-- category is deliberately NOT a CHECK-constrained enum like event_type/revenue_impact/signal -
-- the worked example only shows one value ("Revenue Positive") and Claude produces this as a
-- short free-text label per document (e.g. "Financing", "Ownership Signal"); constraining it to a
-- fixed set here would mean guessing a taxonomy nobody has specified yet.
CREATE TABLE corporate.corporate_events (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         uuid NOT NULL REFERENCES corporate.documents (id) ON DELETE CASCADE,
    instrument_id       uuid NOT NULL,
    symbol              varchar(20) NOT NULL,
    event_type          varchar(30) NOT NULL,
    category            varchar(50) NOT NULL,
    summary             text NOT NULL,
    confidence          numeric(5,2) NOT NULL,
    expected_duration    varchar(50),
    revenue_impact       varchar(10) NOT NULL,
    signal               varchar(10) NOT NULL,
    prompt_version       integer NOT NULL,
    raw_response         jsonb NOT NULL,
    extracted_at         timestamptz NOT NULL,
    created_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_corporate_events_event_type CHECK (event_type IN (
        'LARGE_ORDER', 'CAPACITY_EXPANSION', 'NEW_PLANT', 'ACQUISITION', 'MERGER', 'JOINT_VENTURE',
        'PLI_APPROVAL', 'PATENT', 'EXPORT_APPROVAL', 'GOVERNMENT_CONTRACT', 'DEBT_RAISING',
        'PROMOTER_BUYING', 'PROMOTER_SELLING'
    )),
    CONSTRAINT ck_corporate_events_confidence CHECK (confidence >= 0 AND confidence <= 100),
    CONSTRAINT ck_corporate_events_revenue_impact CHECK (revenue_impact IN ('HIGH', 'MEDIUM', 'LOW', 'NONE')),
    CONSTRAINT ck_corporate_events_signal CHECK (signal IN ('POSITIVE', 'NEGATIVE', 'NEUTRAL'))
);

-- instrument_id references reference.instruments by value only - no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_corporate_events_document_id ON corporate.corporate_events (document_id);
CREATE INDEX ix_corporate_events_instrument_id ON corporate.corporate_events (instrument_id);
CREATE INDEX ix_corporate_events_event_type ON corporate.corporate_events (event_type);

-- EVENTS_EXTRACTED is the new terminal state after event extraction runs over a PROCESSED
-- document - added whether or not any events were actually found, since "no classifiable event"
-- is a real, final outcome, not a reason to retry indefinitely.
ALTER TABLE corporate.documents DROP CONSTRAINT ck_documents_status;
ALTER TABLE corporate.documents ADD CONSTRAINT ck_documents_status
    CHECK (status IN ('PENDING', 'DOWNLOADED', 'PROCESSED', 'NEEDS_OCR', 'FAILED', 'EVENTS_EXTRACTED'));
