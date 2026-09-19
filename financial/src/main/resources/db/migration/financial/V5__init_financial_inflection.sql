-- Business + Earnings Stage 2 (docs/007_Stage2_Inflection_Specification.md §6/§7/§14): interprets
-- financial.transformation_evidence's already-computed change/velocity/persistence into a fixed
-- vocabulary of states, one upsert-latest-per-quarter row per instrument.
--
-- as_of_date is the driving observation's own real period_end, NEVER a daily Clock date -
-- financial evidence is quarterly-cadence, so a Clock-based as_of_date would mint a new row every
-- single day the job runs from the same one quarterly observation (unlike market.inflection_states,
-- where Clock-based as_of_date is correct because that family's evidence genuinely is daily).
-- Re-running the job daily upserts the SAME row until a real new quarter's results arrive.
--
-- driving_metric/level/change/velocity_band/persistence are all null/0 together for
-- NO_CLEAR_SIGNAL - no single metric drove "nothing happened", so none is guessed.
CREATE TABLE financial.inflection_states (
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
    CONSTRAINT ux_financial_inflection_states_instrument_date UNIQUE (instrument_id, as_of_date),
    CONSTRAINT ck_financial_inflection_states_state CHECK (primary_state IN (
        'NO_CLEAR_SIGNAL', 'REVENUE_ACCELERATION', 'PAT_ACCELERATION', 'STRUCTURAL_MARGIN_EXPANSION',
        'OPERATING_LEVERAGE_INFLECTION', 'EARNINGS_INFLECTION_CONVERGENCE'
    ))
);

CREATE INDEX ix_financial_inflection_states_symbol ON financial.inflection_states (symbol);

-- Deleted and reinserted on every recompute of the parent row, matching
-- market.inflection_state_reasons' exact convention.
CREATE TABLE financial.inflection_state_reasons (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    state_id           uuid NOT NULL REFERENCES financial.inflection_states (id) ON DELETE CASCADE,
    reason_code        varchar(60) NOT NULL,
    metric_value       numeric(18, 4) NULL,
    evidence_reference text NULL
);

CREATE INDEX ix_financial_inflection_state_reasons_state ON financial.inflection_state_reasons (state_id);
