-- Capital Allocation Stage 2 (docs/007_Stage2_Inflection_Specification.md §11/§14): interprets
-- corporate.transformation_evidence's already-computed rolling-180-day event counts into a fixed
-- vocabulary of states, one upsert-latest-per-day row per instrument, same convention
-- market.inflection_states already established. as_of_date is Clock-based (not period_end-based
-- like financial.inflection_states) because this family's Stage 1 evidence genuinely is recomputed
-- daily (CapitalAllocationEngine's own rolling 180-day window), matching market's reasoning exactly.
--
-- level/change are integer, not numeric - Tier 1's own BUYBACK_EVENT_COUNT_180D/
-- EQUITY_RAISE_EVENT_COUNT_180D columns are plain whole-number event counts, never fractional.
--
-- driving_metric/level/change/velocity_band/persistence are all null/0 together for
-- NO_CLEAR_SIGNAL - no single metric drove "nothing happened", so none is guessed.
CREATE TABLE corporate.inflection_states (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id     uuid NOT NULL,
    symbol            text NOT NULL,
    as_of_date        date NOT NULL,
    primary_state     varchar(40) NOT NULL,
    driving_metric    varchar(40) NULL,
    level             integer NULL,
    change            integer NULL,
    velocity_band     varchar(10) NULL,
    persistence       integer NOT NULL DEFAULT 0,
    confidence        numeric(5, 2) NOT NULL,
    rule_version      integer NOT NULL DEFAULT 1,
    computed_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_corporate_inflection_states_instrument_date UNIQUE (instrument_id, as_of_date),
    CONSTRAINT ck_corporate_inflection_states_state CHECK (primary_state IN (
        'NO_CLEAR_SIGNAL', 'BUYBACK_ACTIVITY', 'EQUITY_RAISE_ACTIVITY', 'MIXED_CAPITAL_ALLOCATION_ACTIVITY'
    ))
);

CREATE INDEX ix_corporate_inflection_states_symbol ON corporate.inflection_states (symbol);

-- Deleted and reinserted on every recompute of the parent row, matching
-- market.inflection_state_reasons' exact convention.
CREATE TABLE corporate.inflection_state_reasons (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    state_id           uuid NOT NULL REFERENCES corporate.inflection_states (id) ON DELETE CASCADE,
    reason_code        varchar(60) NOT NULL,
    metric_value       numeric(18, 4) NULL,
    evidence_reference text NULL
);

CREATE INDEX ix_corporate_inflection_state_reasons_state ON corporate.inflection_state_reasons (state_id);
