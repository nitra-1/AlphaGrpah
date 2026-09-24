-- Stage 5 Lifecycle Classification: reads ONLY discovery.convergence_snapshots/
-- _domain_contributions (never Stage 3 directly, never recalculates Stage 3/4 logic) and
-- classifies each instrument's Stage 4 history into a 7-state lifecycle trajectory. Mirrors
-- V1__init_discovery_convergence.sql's own column-naming/type conventions exactly
-- (uuid PK default gen_random_uuid(), text for symbol, numeric(5,2) for 0-100 scores,
-- timestamptz default now() for computed_at).
--
-- lifecycle_state is nullable: readiness=INSUFFICIENT_HISTORY or PARTIAL_HISTORY both leave it
-- NULL in v1 (no provisional_state column yet) - only readiness=READY ever produces one of the 7
-- states. Consumers must check lifecycle_readiness before interpreting lifecycle_state as an
-- absence signal.
--
-- peak_lifecycle_stage tracks the highest-ranked state reached during the CURRENT continuous
-- transformation cycle (EARLY_INFLECTION < EMERGING < ACCELERATING < MARKET_RECOGNITION <
-- MATURE_RERATING) - carried forward through a DETERIORATING dip or a partial-recovery state so
-- DETERIORATING-eligibility reflects the cycle's real achievement, not just yesterday's raw state.
-- DORMANT and DETERIORATING are deliberately excluded from the CHECK constraint below - neither is
-- ever itself a "peak": DORMANT resets it to NULL (a genuine return to dormancy ends the cycle),
-- DETERIORATING carries it forward unchanged (decline neither raises nor erases the achievement).
CREATE TABLE discovery.lifecycle_snapshots (
    id                         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id              uuid NOT NULL,
    symbol                     text NOT NULL,
    as_of_date                 date NOT NULL,
    lifecycle_state            varchar(40) NULL,
    lifecycle_readiness        varchar(30) NOT NULL,
    trajectory_direction       varchar(20) NOT NULL,
    peak_lifecycle_stage       varchar(40) NULL,
    lifecycle_strength         numeric(5, 2) NULL,
    trajectory_score           numeric(5, 2) NULL,
    current_convergence_score  numeric(5, 2) NULL,
    peak_convergence_score     numeric(5, 2) NULL,
    current_active_domains     integer NULL,
    peak_active_domains        integer NULL,
    lifecycle_started_date     date NULL,
    state_started_date         date NULL,
    lifecycle_age_days         integer NULL,
    rule_version               integer NOT NULL DEFAULT 1,
    computed_at                timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_discovery_lifecycle_snapshots_instrument_date UNIQUE (instrument_id, as_of_date),
    CONSTRAINT ck_discovery_lifecycle_snapshots_state CHECK (lifecycle_state IN (
        'DORMANT', 'EARLY_INFLECTION', 'EMERGING', 'ACCELERATING',
        'MARKET_RECOGNITION', 'MATURE_RERATING', 'DETERIORATING'
    )),
    CONSTRAINT ck_discovery_lifecycle_snapshots_readiness CHECK (lifecycle_readiness IN (
        'INSUFFICIENT_HISTORY', 'PARTIAL_HISTORY', 'READY'
    )),
    CONSTRAINT ck_discovery_lifecycle_snapshots_direction CHECK (trajectory_direction IN (
        'RISING', 'STABLE', 'WEAKENING', 'RECOVERING', 'UNKNOWN'
    )),
    CONSTRAINT ck_discovery_lifecycle_snapshots_peak CHECK (peak_lifecycle_stage IN (
        'EARLY_INFLECTION', 'EMERGING', 'ACCELERATING', 'MARKET_RECOGNITION', 'MATURE_RERATING'
    ))
);

COMMENT ON COLUMN discovery.lifecycle_snapshots.lifecycle_state IS
    'NULL means readiness is not READY (INSUFFICIENT_HISTORY or PARTIAL_HISTORY) - never a fake DORMANT. Consumers must check lifecycle_readiness before interpreting this column.';

CREATE INDEX ix_discovery_lifecycle_snapshots_symbol ON discovery.lifecycle_snapshots (symbol);

-- One row per real state change - answers "when did X become EMERGING/enter
-- MARKET_RECOGNITION/start deteriorating" directly, without scanning lifecycle_snapshots.
-- UNIQUE is per (instrument_id, transition_date) only, not per to_state - exactly one
-- authoritative transition per instrument per date; a same-day rerun with fresher upstream data
-- replaces the prior candidate rather than accumulating a second row (see
-- DiscoveryLifecycleWriter). trigger_reason = 'INITIAL_LIFECYCLE_CLASSIFICATION' (with
-- from_state = NULL) marks the very first authoritative classification for an instrument,
-- distinguished from an ordinary transition.
CREATE TABLE discovery.lifecycle_transitions (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id          uuid NOT NULL,
    symbol                 text NOT NULL,
    transition_date        date NOT NULL,
    from_state             varchar(40) NULL,
    to_state               varchar(40) NOT NULL,
    trigger_reason         varchar(60) NOT NULL,
    convergence_score      numeric(5, 2) NULL,
    active_domain_count    integer NULL,
    evidence_reference     text NULL,
    computed_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_discovery_lifecycle_transitions_instrument_date UNIQUE (instrument_id, transition_date),
    CONSTRAINT ck_discovery_lifecycle_transitions_from_state CHECK (from_state IN (
        'DORMANT', 'EARLY_INFLECTION', 'EMERGING', 'ACCELERATING',
        'MARKET_RECOGNITION', 'MATURE_RERATING', 'DETERIORATING'
    )),
    CONSTRAINT ck_discovery_lifecycle_transitions_to_state CHECK (to_state IN (
        'DORMANT', 'EARLY_INFLECTION', 'EMERGING', 'ACCELERATING',
        'MARKET_RECOGNITION', 'MATURE_RERATING', 'DETERIORATING'
    ))
);

CREATE INDEX ix_discovery_lifecycle_transitions_instrument ON discovery.lifecycle_transitions (instrument_id, transition_date);
CREATE INDEX ix_discovery_lifecycle_transitions_symbol ON discovery.lifecycle_transitions (symbol);

-- Deleted and reinserted on every recompute of the parent row, matching every Stage 2/3/4 reasons
-- table's exact convention.
CREATE TABLE discovery.lifecycle_reasons (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id        uuid NOT NULL REFERENCES discovery.lifecycle_snapshots (id) ON DELETE CASCADE,
    reason_code        varchar(60) NOT NULL,
    metric_name        varchar(60) NULL,
    metric_value       numeric(18, 4) NULL,
    evidence_date      date NULL,
    evidence_reference text NULL
);

CREATE INDEX ix_discovery_lifecycle_reasons_snapshot ON discovery.lifecycle_reasons (snapshot_id);
