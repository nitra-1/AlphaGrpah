-- Market Accumulation Stage 2 (docs/007_Stage2_Inflection_Specification.md §10/§14): interprets
-- market.transformation_evidence's already-computed change/velocity/persistence into a fixed
-- vocabulary of states, one upsert-latest-per-day row per instrument, same convention
-- ownership.transformation_states already established.
--
-- driving_metric/level/change/velocity_band/persistence are all null/0 together for
-- NO_CLEAR_SIGNAL - no single metric drove "nothing happened", so none is guessed.
CREATE TABLE market.inflection_states (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id     uuid NOT NULL,
    symbol            text NOT NULL,
    as_of_date        date NOT NULL,
    primary_state     varchar(40) NOT NULL,
    driving_metric    varchar(40) NULL,
    level             numeric(14, 4) NULL,
    change            numeric(14, 4) NULL,
    velocity_band     varchar(10) NULL,
    persistence       integer NOT NULL DEFAULT 0,
    confidence        numeric(5, 2) NOT NULL,
    rule_version      integer NOT NULL DEFAULT 1,
    computed_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_market_inflection_states_instrument_date UNIQUE (instrument_id, as_of_date),
    CONSTRAINT ck_market_inflection_states_state CHECK (primary_state IN (
        'NO_CLEAR_SIGNAL', 'DELIVERY_EXPANSION', 'RELATIVE_VOLUME_EXPANSION',
        'SUSTAINED_DELIVERY_ACCUMULATION', 'STEALTH_ACCUMULATION_CANDIDATE', 'EARLY_PRICE_PARTICIPATION'
    ))
);

CREATE INDEX ix_market_inflection_states_symbol ON market.inflection_states (symbol);

-- Deleted and reinserted on every recompute of the parent row, matching
-- ownership.transformation_state_reasons' exact convention.
CREATE TABLE market.inflection_state_reasons (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    state_id           uuid NOT NULL REFERENCES market.inflection_states (id) ON DELETE CASCADE,
    reason_code        varchar(60) NOT NULL,
    metric_value       numeric(18, 4) NULL,
    evidence_reference text NULL
);

CREATE INDEX ix_market_inflection_state_reasons_state ON market.inflection_state_reasons (state_id);
