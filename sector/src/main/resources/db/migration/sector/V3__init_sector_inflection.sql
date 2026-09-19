-- Sector Stage 2 (docs/007_Stage2_Inflection_Specification.md §12/§14): interprets
-- sector.transformation_evidence's already-computed change/persistence into a fixed vocabulary of
-- states, one upsert-latest-per-instrument row per real evidence date, same convention
-- market.inflection_states already established.
--
-- as_of_date is the driving observation's own real evidence date, NEVER a daily Clock date -
-- unlike Market's 3 metrics (all genuinely daily), only SECTOR_RELATIVE_STRENGTH here is reliably
-- daily; INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY/_VS_SECTOR are real but thin/empty today, so a
-- Clock-based as_of_date would manufacture apparent daily persistence from one stale observation.
--
-- evidence_coverage_pct/data_readiness are always populated (even on a firing state) - distinct
-- from primary_state, they report how many of the 3 source metrics have any real evidence at all
-- for this instrument today, so "NO_CLEAR_SIGNAL because nothing's happening" (READY) stays
-- honestly distinguishable from "NO_CLEAR_SIGNAL because 2 of 3 sources don't exist yet"
-- (PARTIAL_DATA/INSUFFICIENT_DATA).
--
-- driving_metric/level/change/velocity_band/persistence are all null/0 together for
-- NO_CLEAR_SIGNAL - no single metric drove "nothing happened", so none is guessed.
CREATE TABLE sector.inflection_states (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id          uuid NOT NULL,
    symbol                 text NOT NULL,
    as_of_date             date NOT NULL,
    primary_state          varchar(40) NOT NULL,
    driving_metric         varchar(40) NULL,
    level                  numeric(14, 4) NULL,
    change                 numeric(14, 4) NULL,
    velocity_band          varchar(10) NULL,
    persistence            integer NOT NULL DEFAULT 0,
    evidence_coverage_pct  integer NOT NULL,
    data_readiness         varchar(20) NOT NULL,
    confidence             numeric(5, 2) NOT NULL,
    rule_version           integer NOT NULL DEFAULT 1,
    computed_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_sector_inflection_states_instrument_date UNIQUE (instrument_id, as_of_date),
    CONSTRAINT ck_sector_inflection_states_state CHECK (primary_state IN (
        'NO_CLEAR_SIGNAL', 'SECTOR_STRENGTHENING', 'STOCK_OUTPERFORMING_NIFTY',
        'STOCK_OUTPERFORMING_SECTOR', 'NEW_LEADERSHIP_EMERGENCE'
    )),
    CONSTRAINT ck_sector_inflection_states_readiness CHECK (data_readiness IN (
        'READY', 'PARTIAL_DATA', 'INSUFFICIENT_DATA'
    ))
);

CREATE INDEX ix_sector_inflection_states_symbol ON sector.inflection_states (symbol);

-- Deleted and reinserted on every recompute of the parent row, matching
-- market.inflection_state_reasons' exact convention.
CREATE TABLE sector.inflection_state_reasons (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    state_id           uuid NOT NULL REFERENCES sector.inflection_states (id) ON DELETE CASCADE,
    reason_code        varchar(60) NOT NULL,
    metric_value       numeric(18, 4) NULL,
    evidence_reference text NULL
);

CREATE INDEX ix_sector_inflection_state_reasons_state ON sector.inflection_state_reasons (state_id);
