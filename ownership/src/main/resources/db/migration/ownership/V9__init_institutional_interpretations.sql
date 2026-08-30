-- Sprint 3: one row per (symbol, as_of_date), upserted daily - unlike deal_materiality's append-
-- only-per-deal design, this is a "latest interpretation per symbol per day" table (matching
-- ownership.institutional_scores' own upsert convention), since re-running the same day's job as
-- new deals arrive should refresh that day's row, not append duplicates. The per-day ledger this
-- naturally creates is what lets the T+1/T+3/T+5 discovery-confirmation progression be read back
-- later without a separate history table.
--
-- event_structure / institutional_state / discovery_confirmation_state / confidence are
-- deliberately four separate fields, never blended into one score - see
-- ownership.interpretation.InstitutionalInterpretationEngine's own doc comment for why.
--
-- discovery_confirmation_state is only ever computed for a directional institutional_state
-- (POSSIBLE_ACCUMULATION/POSSIBLE_DISTRIBUTION) - NOT_APPLICABLE otherwise, with every
-- confirmation-specific column left null. event_anchor_date/confirmation_sessions_elapsed/
-- confirmation_frozen implement the bounded T+1/T+3/T+5-trading-session lifecycle: the anchor only
-- advances for a same-direction, materially-relevant (materiality >= MEDIUM) deal - never any new
-- deal - and confirmation freezes permanently once 5 real trading sessions exist after the anchor.
-- The four component scores (each already weighted per the DiscoveryConfirmationEngine design,
-- 35/25/20/20) and the coverage percentage are persisted alongside the final banded state and
-- score, not discarded after banding - future validation needs to know which component actually
-- carried predictive weight, not just the final label.
CREATE TABLE ownership.institutional_interpretations (
    id                                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol                              text NOT NULL,
    as_of_date                          date NOT NULL,
    event_structure                     varchar(40) NOT NULL,
    institutional_state                 varchar(20) NOT NULL,
    discovery_confirmation_state        varchar(20) NOT NULL,
    confirmation_frozen                 boolean NOT NULL DEFAULT false,
    event_anchor_date                   date NULL,
    confirmation_sessions_elapsed       integer NOT NULL DEFAULT 0,
    confirmation_score                  numeric(5, 2) NULL,
    price_confirmation_score            numeric(5, 2) NULL,
    delivery_confirmation_score         numeric(5, 2) NULL,
    volume_confirmation_score           numeric(5, 2) NULL,
    repeat_activity_confirmation_score  numeric(5, 2) NULL,
    confirmation_coverage_pct           numeric(5, 2) NULL,
    confidence                          numeric(5, 2) NOT NULL,
    materiality_score                   numeric(5, 2) NULL,
    reported_flow_state                 varchar(20) NULL,
    churn_state                         varchar(20) NOT NULL,
    institutional_buy_value             numeric(18, 2) NOT NULL DEFAULT 0,
    institutional_sell_value            numeric(18, 2) NOT NULL DEFAULT 0,
    institutional_buyer_count           integer NOT NULL DEFAULT 0,
    institutional_seller_count          integer NOT NULL DEFAULT 0,
    rule_version                        integer NOT NULL,
    computed_at                         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_institutional_interpretations_symbol_date UNIQUE (symbol, as_of_date),
    CONSTRAINT ck_institutional_interpretations_event_structure CHECK (event_structure IN (
        'PROP_CHURN', 'HIGH_CHURN_ACTIVITY', 'DIRECTIONAL_BUYING', 'DIRECTIONAL_SELLING',
        'INSTITUTIONAL_BUYING_CANDIDATE', 'INSTITUTIONAL_SELLING_CANDIDATE', 'MULTI_INSTITUTION_BUYING',
        'SINGLE_INSTITUTION_POSITION_BUILDING', 'MIXED_ACTIVITY', 'UNRESOLVED'
    )),
    CONSTRAINT ck_institutional_interpretations_state CHECK (institutional_state IN (
        'NO_CLEAR_SIGNAL', 'HIGH_CHURN', 'POSSIBLE_ACCUMULATION', 'POSSIBLE_DISTRIBUTION', 'MIXED_ACTIVITY'
    )),
    CONSTRAINT ck_institutional_interpretations_confirmation CHECK (discovery_confirmation_state IN (
        'PENDING', 'PARTIALLY_CONFIRMED', 'CONFIRMED', 'FAILED', 'NOT_APPLICABLE'
    )),
    CONSTRAINT ck_institutional_interpretations_churn CHECK (churn_state IN (
        'DIRECTIONAL', 'MIXED', 'HIGH_CHURN', 'VERY_HIGH_CHURN'
    ))
);

CREATE INDEX ix_institutional_interpretations_symbol ON ownership.institutional_interpretations (symbol);

-- Deleted and reinserted on every recompute of the parent row (matches the parent's upsert
-- semantics) - human-readable, persisted evidence (e.g. VERY_HIGH_MATERIALITY, LOW_CHURN,
-- THREE_INSTITUTIONAL_BUYERS) so a conclusion is never stored without its reasons.
CREATE TABLE ownership.institutional_interpretation_reasons (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    interpretation_id  uuid NOT NULL REFERENCES ownership.institutional_interpretations (id) ON DELETE CASCADE,
    reason_code        varchar(60) NOT NULL,
    metric_value       numeric(18, 4) NULL,
    evidence_reference text NULL
);

CREATE INDEX ix_institutional_interpretation_reasons_interpretation ON ownership.institutional_interpretation_reasons (interpretation_id);
