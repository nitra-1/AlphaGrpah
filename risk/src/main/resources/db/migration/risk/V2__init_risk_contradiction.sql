-- Risk/Contradiction Stage 2 (docs/007_Stage2_Inflection_Specification.md §13/§14): the last of the
-- 6 Stage 2 families, a meta-layer over 4 already-built families' own Stage 2 states, not raw
-- Stage 1 evidence directly. Computed in intelligence.riskcontradiction, written here (a domain's
-- own schema is always written by a class that domain module owns, never directly from
-- intelligence) - same convention sector.transformation_evidence/corporate.inflection_states
-- already established.
--
-- A completely separate concept from risk.risk_scores (Module 1.9's aggregate 0-100 Risk Engine
-- score, unrelated) - this table is never read or written by risk.engine.RiskEngine.
--
-- No driving_metric/level/change/velocity_band/persistence columns - unlike every other Stage 2
-- family, this one's states are boolean composites of OTHER families' already-decided states, not
-- a single banded numeric metric of its own. §13's own trigger table has no "Persistence rule"
-- column either, so persistence is genuinely absent from this family, not merely zeroed out.
--
-- evidence_coverage_pct/data_readiness mirror sector.inflection_states' own columns - how many of
-- the (up to 6) real cross-family reads this row's computation actually found data for, so
-- NO_CLEAR_SIGNAL stays honestly distinguishable from "most sources have no data for this
-- instrument yet" versus genuinely calm real data.
CREATE TABLE risk.contradiction_states (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id          uuid NOT NULL,
    symbol                 text NOT NULL,
    as_of_date             date NOT NULL,
    primary_state          varchar(40) NOT NULL,
    evidence_coverage_pct  integer NOT NULL,
    data_readiness         varchar(20) NOT NULL,
    confidence             numeric(5, 2) NOT NULL,
    rule_version           integer NOT NULL DEFAULT 1,
    computed_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_risk_contradiction_states_instrument_date UNIQUE (instrument_id, as_of_date),
    CONSTRAINT ck_risk_contradiction_states_state CHECK (primary_state IN (
        'NO_CLEAR_SIGNAL', 'GROWTH_QUALITY_CONTRADICTION', 'OWNERSHIP_CONTRADICTION',
        'PRICE_WITHOUT_DELIVERY_CONFIRMATION', 'CAPITAL_RAISE_WITH_WEAK_BUSINESS_INFLECTION',
        'MULTI_DOMAIN_CONTRADICTION'
    )),
    CONSTRAINT ck_risk_contradiction_states_readiness CHECK (data_readiness IN (
        'READY', 'PARTIAL_DATA', 'INSUFFICIENT_DATA'
    ))
);

CREATE INDEX ix_risk_contradiction_states_symbol ON risk.contradiction_states (symbol);

-- Deleted and reinserted on every recompute of the parent row, matching every other family's
-- reasons table exact convention.
CREATE TABLE risk.contradiction_state_reasons (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    state_id           uuid NOT NULL REFERENCES risk.contradiction_states (id) ON DELETE CASCADE,
    reason_code        varchar(60) NOT NULL,
    metric_value       numeric(18, 4) NULL,
    evidence_reference text NULL
);

CREATE INDEX ix_risk_contradiction_state_reasons_state ON risk.contradiction_state_reasons (state_id);
