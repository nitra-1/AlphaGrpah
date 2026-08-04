-- Module 2.5: Management Commentary Engine. Two layers, mirroring Order Book's exact shape
-- (order_book_ledger -> order_book_snapshots): management_observations is Layer 1 (immutable,
-- one row per guidance statement, never revised), management_commentary_snapshots is Layer 2
-- (derived, a genuine common.engine.Engine implementation like Order Book Engine).

-- fact_group lets one Stage 2 extractor emit several facts that belong to the SAME logical
-- record within one document, correlated by a shared id - needed because, unlike Order Book
-- (at most one order per document, so all its facts implicitly belong together),
-- ManagementExtractor can find several distinct guidance statements in a single earnings call
-- (revenue guidance AND margin guidance AND capex commentary all at once). Nullable and unused by
-- OrderExtractor - a document with exactly one structured record per document needs no grouping.
ALTER TABLE corporate.document_facts ADD COLUMN fact_group uuid;
CREATE INDEX ix_document_facts_fact_group ON corporate.document_facts (document_id, fact_group);

-- One extracted forward-looking statement. metric_type is deliberately NOT constrained to the 9
-- named categories (Revenue Guidance, Margin Guidance, Capex, Demand, Pricing, Competition,
-- Hiring, Exports, Risk) via a CHECK - same reasoning as corporate_events.category: these are the
-- categories asked for in the extraction prompt, not a promise no other value can ever appear.
-- guidance_value_numeric is nullable - most Revenue/Margin guidance is a percentage and parses
-- cleanly, but Demand/Pricing/Competition/Risk commentary is usually qualitative prose with
-- nothing to parse; leaving it null there is honest, not a parsing failure.
CREATE TABLE corporate.management_observations (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id           uuid NOT NULL REFERENCES corporate.documents (id) ON DELETE CASCADE,
    instrument_id         uuid NOT NULL,
    symbol                varchar(20) NOT NULL,
    metric_type           varchar(30) NOT NULL,
    guidance_value        text NOT NULL,
    guidance_value_numeric numeric(10,2),
    guidance_period       varchar(100),
    direction             varchar(10) NOT NULL,
    signal                varchar(100) NOT NULL,
    commitment_level      varchar(10) NOT NULL,
    extraction_confidence numeric(5,2) NOT NULL,
    observed_at           timestamptz NOT NULL,
    created_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_management_observations_direction CHECK (direction IN ('POSITIVE', 'NEGATIVE', 'NEUTRAL')),
    CONSTRAINT ck_management_observations_commitment CHECK (commitment_level IN ('LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH'))
);

CREATE INDEX ix_management_observations_instrument_id ON corporate.management_observations (instrument_id);
CREATE INDEX ix_management_observations_metric_type ON corporate.management_observations (metric_type);

-- Layer 2: one point-in-time aggregation per instrument, mirroring order_book_snapshots exactly.
-- guidance_trend is UNKNOWN (not fabricated as STABLE) when fewer than 2 numeric revenue-guidance
-- observations exist to compare.
CREATE TABLE corporate.management_commentary_snapshots (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id           uuid NOT NULL,
    symbol                  varchar(20) NOT NULL,
    as_of_date              date NOT NULL,
    growth_visibility_score numeric(5,2) NOT NULL,
    guidance_trend          varchar(15) NOT NULL,
    management_credibility  varchar(10) NOT NULL,
    confidence              numeric(5,2) NOT NULL,
    rule_set_version        integer NOT NULL,
    computed_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_management_commentary_snapshots_trend CHECK (guidance_trend IN ('UPGRADING', 'STABLE', 'DOWNGRADING', 'UNKNOWN')),
    CONSTRAINT ck_management_commentary_snapshots_credibility CHECK (management_credibility IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ux_management_commentary_snapshots_instrument_date UNIQUE (instrument_id, as_of_date)
);

CREATE INDEX ix_management_commentary_snapshots_instrument_id ON corporate.management_commentary_snapshots (instrument_id);
