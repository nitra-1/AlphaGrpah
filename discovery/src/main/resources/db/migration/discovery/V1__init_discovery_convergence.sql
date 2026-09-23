-- Stage 4 Cross-Domain Convergence: consumes Market/Ownership/Financial/Sector/Capital
-- Allocation's own Stage 3 transformation_sequences tables directly (never Stage 2, never each
-- other's package-private records - see discovery.convergence's own readers), plus
-- risk.contradiction_states as a contradiction OVERLAY, never a 6th positive domain.
--
-- Scores are nullable, not defaulted to 0: a row with readiness = INSUFFICIENT_DATA persists
-- every score/penalty column as NULL, meaning genuinely unevaluated - never a real zero
-- convergence score. Consumers must check readiness before interpreting any score column.
-- domain_coverage_count/domain_coverage_pct stay NOT NULL - always computable independent of
-- scoring (mirrors risk.contradiction_states.evidence_coverage_pct's own integer convention, not
-- numeric).
--
-- pre_contradiction_state is the convergence classification computed from the raw, pre-penalty
-- picture alone; convergence_state is the DISPLAY state, which only ever overlays
-- CONVERGENCE_WITH_CONTRADICTIONS on top of an actual positive pre_contradiction_state
-- (EARLY_CONVERGENCE/MULTI_DOMAIN_INFLECTION/STRONG_CONVERGENCE) - NO_CONVERGENCE plus a live
-- contradiction stays NO_CONVERGENCE, since there is no convergence to contradict.
CREATE TABLE discovery.convergence_snapshots (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id             uuid NOT NULL,
    symbol                    text NOT NULL,
    as_of_date                date NOT NULL,
    convergence_state         varchar(40) NOT NULL,
    pre_contradiction_state   varchar(40) NULL,
    active_domain_count       integer NOT NULL,
    qualifying_sequence_count integer NOT NULL,
    domain_coverage_count     integer NOT NULL,
    domain_coverage_pct       integer NOT NULL,
    breadth_score             numeric(5, 2) NULL,
    maturity_score            numeric(5, 2) NULL,
    recency_score             numeric(5, 2) NULL,
    confidence_score          numeric(5, 2) NULL,
    density_score             numeric(5, 2) NULL,
    raw_convergence_score     numeric(5, 2) NULL,
    contradiction_penalty     numeric(5, 2) NULL,
    convergence_score         numeric(5, 2) NULL,
    earliest_supporting_date  date NULL,
    latest_supporting_date    date NULL,
    readiness                 varchar(30) NOT NULL,
    rule_version              integer NOT NULL DEFAULT 1,
    computed_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_discovery_convergence_snapshots_instrument_date UNIQUE (instrument_id, as_of_date),
    CONSTRAINT ck_discovery_convergence_snapshots_state CHECK (convergence_state IN (
        'NO_CONVERGENCE', 'EARLY_CONVERGENCE', 'MULTI_DOMAIN_INFLECTION',
        'STRONG_CONVERGENCE', 'CONVERGENCE_WITH_CONTRADICTIONS'
    )),
    CONSTRAINT ck_discovery_convergence_snapshots_pre_state CHECK (pre_contradiction_state IN (
        'NO_CONVERGENCE', 'EARLY_CONVERGENCE', 'MULTI_DOMAIN_INFLECTION', 'STRONG_CONVERGENCE'
    )),
    CONSTRAINT ck_discovery_convergence_snapshots_readiness CHECK (readiness IN (
        'READY', 'PARTIAL_DATA', 'INSUFFICIENT_DATA'
    ))
);

COMMENT ON COLUMN discovery.convergence_snapshots.convergence_score IS
    'NULL means genuinely unevaluated (readiness=INSUFFICIENT_DATA), never a real zero convergence score - consumers must check readiness before interpreting any score column.';

CREATE INDEX ix_discovery_convergence_snapshots_symbol ON discovery.convergence_snapshots (symbol);

-- One row per (snapshot, domain) - always exactly the 5 positive domains, even when a domain has
-- no data at all (contribution_status = NO_ACTIVE_SEQUENCE/INSUFFICIENT_DATA), so a snapshot's
-- full domain picture is always reconstructable without inferring absence.
CREATE TABLE discovery.convergence_domain_contributions (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id            uuid NOT NULL REFERENCES discovery.convergence_snapshots (id) ON DELETE CASCADE,
    domain                 varchar(40) NOT NULL,
    contribution_status    varchar(30) NOT NULL,
    active_sequence_count  integer NOT NULL,
    strongest_phase        varchar(20) NULL,
    domain_strength        numeric(5, 2) NULL,
    domain_confidence      numeric(5, 2) NULL,
    earliest_sequence_date date NULL,
    latest_sequence_date   date NULL,
    contribution_score     numeric(5, 2) NULL,
    evidence_reference     text NULL,
    CONSTRAINT ux_discovery_convergence_domain_contributions_snapshot_domain UNIQUE (snapshot_id, domain),
    CONSTRAINT ck_discovery_convergence_domain_contributions_domain CHECK (domain IN (
        'FINANCIAL', 'OWNERSHIP', 'MARKET', 'SECTOR', 'CAPITAL_ALLOCATION'
    )),
    CONSTRAINT ck_discovery_convergence_domain_contributions_status CHECK (contribution_status IN (
        'ACTIVE', 'STALE', 'BROKEN', 'INSUFFICIENT_DATA', 'NO_ACTIVE_SEQUENCE'
    )),
    CONSTRAINT ck_discovery_convergence_domain_contributions_phase CHECK (strongest_phase IN (
        'FORMING', 'PROGRESSING', 'COMPLETE', 'BROKEN'
    ))
);

CREATE INDEX ix_discovery_convergence_domain_contributions_snapshot ON discovery.convergence_domain_contributions (snapshot_id);

-- Deleted and reinserted on every recompute of the parent row, matching every Stage 2/3 reasons
-- table's exact convention.
CREATE TABLE discovery.convergence_reasons (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id        uuid NOT NULL REFERENCES discovery.convergence_snapshots (id) ON DELETE CASCADE,
    reason_code        varchar(60) NOT NULL,
    domain             varchar(40) NULL,
    sequence_type      varchar(60) NULL,
    metric_value       numeric(18, 4) NULL,
    evidence_date      date NULL,
    evidence_reference text NULL
);

CREATE INDEX ix_discovery_convergence_reasons_snapshot ON discovery.convergence_reasons (snapshot_id);
