-- Ownership Transformation Engine (Multibagger Discovery, first evidence-family vertical slice):
-- replaces the static shareholding sample with a live per-symbol NSE source
-- (corporate-share-holdings-master + its linked XBRL filing), then turns real quarterly history
-- into a versioned evidence ledger and banded transformation states. See claude.md for the full
-- investigation/design writeup.
--
-- fii_percentage/dii_percentage go nullable: the live summary JSON only carries promoter/public
-- directly - FII/DII stay null between the daily collector run and the (slightly later) XBRL
-- enrichment pass that derives them from the real filing. ownership.engine.InstitutionalEngine
-- already null-tolerates missing shareholding metrics (confidence scales down, no crash) - the
-- same behavior mf_percentage/public_percentage already exercise today, just now applying to two
-- more columns some of the time.
ALTER TABLE ownership.shareholding_pattern
    ADD COLUMN fpi_category_1_percentage numeric(5, 2) NULL,
    ADD COLUMN fpi_category_2_percentage numeric(5, 2) NULL,
    ADD COLUMN insurance_companies_percentage numeric(5, 2) NULL,
    ADD COLUMN other_financial_institutions_percentage numeric(5, 2) NULL,
    ALTER COLUMN fii_percentage DROP NOT NULL,
    ALTER COLUMN dii_percentage DROP NOT NULL;

-- The real XBRL filing URL for one instrument/quarter, captured at shareholding-collection time
-- (see ownership.pattern.ShareholdingXbrlUrlWriter's best-effort side-channel write, the same
-- "capture something extra at normalize-time without changing the return type" pattern
-- ownership.deals.DiscoveredDealWriter established for bulk deals) so the XBRL enrichment pass
-- never needs to re-fetch the summary JSON a second time just to re-derive a URL it already saw.
CREATE TABLE ownership.shareholding_xbrl_urls (
    instrument_id uuid NOT NULL,
    period_end    date NOT NULL,
    xbrl_url      text NOT NULL,
    captured_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (instrument_id, period_end)
);

-- Append-only evidence ledger, one row per (instrument, metric, quarter transition) - never
-- updated in place, same real-historical-ledger convention ownership.deal_materiality (V6) uses,
-- since this is explicitly meant to become "the raw evidence layer for later learning" and must
-- never be silently overwritten. The unique constraint is what makes the writer's
-- ON CONFLICT DO NOTHING both append-only and safely re-runnable on the same day.
CREATE TABLE ownership.transformation_evidence (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id           uuid NOT NULL,
    symbol                  text NOT NULL,
    metric_name             varchar(40) NOT NULL,
    period_end              date NOT NULL,
    prior_period_end        date NULL,
    value                   numeric(7, 4) NOT NULL,
    prior_value             numeric(7, 4) NULL,
    change_pp               numeric(7, 4) NULL,
    velocity_pp_per_quarter numeric(7, 4) NULL,
    persistence_quarters    integer NOT NULL DEFAULT 0,
    confidence              numeric(5, 2) NOT NULL,
    source                  varchar(30) NOT NULL DEFAULT 'NSE_SHAREHOLDING_XBRL',
    rule_version            integer NOT NULL,
    computed_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_transformation_evidence_metric CHECK (metric_name IN (
        'PROMOTER', 'FII', 'DII', 'MF', 'PUBLIC',
        'FPI_CATEGORY_1', 'FPI_CATEGORY_2', 'INSURANCE_COMPANIES', 'OTHER_FINANCIAL_INSTITUTIONS'
    )),
    CONSTRAINT ux_transformation_evidence_instrument_metric_period UNIQUE (instrument_id, metric_name, period_end)
);

CREATE INDEX ix_transformation_evidence_instrument_id ON ownership.transformation_evidence (instrument_id);

-- "Latest classification per instrument per day", upserted daily - same convention
-- ownership.institutional_interpretations (V9) uses, distinct from transformation_evidence's
-- append-only ledger above: this is a current state, not a historical fact.
--
-- Not every state that fires is mutually exclusive with the others (e.g. FII_ACCUMULATION and
-- INSTITUTIONAL_OWNERSHIP_EXPANSION can both genuinely apply in the same quarter) - a single
-- primary_state is chosen by a hardcoded Java priority ladder
-- (ownership.transformation.OwnershipTransformationEngine), but every state that actually fired is
-- still recorded as a reason code below, so a contradiction never hides the accumulation that
-- co-occurred with it.
--
-- BULK_BUYING_WITH_OWNERSHIP_EXPANSION is named deliberately, not
-- "ACCUMULATION_VALIDATED_BY_OWNERSHIP": ownership.bulk_deals carries no participant-type
-- classification, so this state is a disclosed correlation between two unlinked real
-- observations (reported bulk/block buying, then a later ownership increase), not a claim of
-- confirmed institutional origin.
CREATE TABLE ownership.transformation_states (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id         uuid NOT NULL,
    symbol                text NOT NULL,
    as_of_date            date NOT NULL,
    latest_period_end     date NOT NULL,
    prior_period_end      date NULL,
    transformation_state  varchar(40) NOT NULL,
    confidence            numeric(5, 2) NOT NULL,
    rule_version          integer NOT NULL,
    computed_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_transformation_states_instrument_date UNIQUE (instrument_id, as_of_date),
    CONSTRAINT ck_transformation_states_state CHECK (transformation_state IN (
        'NO_CLEAR_SIGNAL', 'PROMOTER_HOLDING_INCREASE', 'PROMOTER_DILUTION',
        'FII_ACCUMULATION', 'DII_ACCUMULATION', 'INSTITUTIONAL_OWNERSHIP_EXPANSION',
        'BULK_BUYING_WITH_OWNERSHIP_EXPANSION', 'OWNERSHIP_CONTRADICTION'
    ))
);

CREATE INDEX ix_transformation_states_symbol ON ownership.transformation_states (symbol);

-- Deleted and reinserted on every recompute of the parent row, matching
-- institutional_interpretation_reasons' exact convention.
CREATE TABLE ownership.transformation_state_reasons (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    state_id           uuid NOT NULL REFERENCES ownership.transformation_states (id) ON DELETE CASCADE,
    reason_code        varchar(60) NOT NULL,
    metric_value       numeric(18, 4) NULL,
    evidence_reference text NULL
);

CREATE INDEX ix_transformation_state_reasons_state ON ownership.transformation_state_reasons (state_id);
