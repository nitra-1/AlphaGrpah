-- Ownership Stage 3 sequence detection (docs/008_Stage3_Sequence_Detection_Specification.md §14.2):
-- converts ownership.transformation_states/_state_reasons history into ordered transformation
-- patterns. One row per (instrument, as_of_date, sequence_type) - a stock may legitimately have
-- multiple simultaneous sequences. Mirrors market.transformation_sequences (V7) exactly except
-- schema-qualified for ownership, sequence_type values, and history_periods (reporting periods -
-- distinct latest_period_end quarters - not trading sessions, docs/008 §6).
--
-- No driving_metric/level/change/velocity_band columns - a sequence's "evidence" is its own step
-- history (ownership.transformation_sequence_reasons), not a single banded numeric metric.
CREATE TABLE ownership.transformation_sequences (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id     uuid NOT NULL,
    symbol            text NOT NULL,
    as_of_date        date NOT NULL,
    sequence_type     varchar(60) NOT NULL,
    sequence_phase    varchar(20) NOT NULL,
    current_step      integer NOT NULL,
    total_steps       integer NOT NULL,
    first_step_date   date NULL,
    last_step_date    date NULL,
    sequence_strength numeric(5, 2) NOT NULL,
    confidence        numeric(5, 2) NOT NULL,
    rule_version      integer NOT NULL DEFAULT 1,
    computed_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_ownership_transformation_sequences_instrument_date_type UNIQUE (instrument_id, as_of_date, sequence_type),
    CONSTRAINT ck_ownership_transformation_sequences_type CHECK (sequence_type IN (
        'INSTITUTIONAL_OWNERSHIP_BUILDING', 'BROAD_INSTITUTIONAL_PARTICIPATION', 'PROMOTER_INSTITUTION_ALIGNMENT'
    )),
    CONSTRAINT ck_ownership_transformation_sequences_phase CHECK (sequence_phase IN (
        'FORMING', 'PROGRESSING', 'COMPLETE', 'BROKEN'
    ))
);

CREATE INDEX ix_ownership_transformation_sequences_symbol ON ownership.transformation_sequences (symbol);

-- Deleted and reinserted on every recompute of the parent row, matching every Stage 2 reasons
-- table's exact convention.
CREATE TABLE ownership.transformation_sequence_reasons (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_id        uuid NOT NULL REFERENCES ownership.transformation_sequences (id) ON DELETE CASCADE,
    reason_code        varchar(60) NOT NULL,
    metric_value       numeric(18, 4) NULL,
    evidence_reference text NULL
);

CREATE INDEX ix_ownership_transformation_sequence_reasons_sequence ON ownership.transformation_sequence_reasons (sequence_id);

-- One row per (instrument, as_of_date) - NOT per sequence_type - written every real run for every
-- instrument with any real Ownership Stage 1 evidence, independent of whether any sequence fired.
-- history_periods is the real distinct-quarter count found in the fetched, canonicalized lookback
-- window (docs/008 §6, §12).
CREATE TABLE ownership.transformation_sequence_readiness (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id     uuid NOT NULL,
    symbol            text NOT NULL,
    as_of_date        date NOT NULL,
    history_periods   integer NOT NULL,
    readiness         varchar(30) NOT NULL,
    computed_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_ownership_transformation_sequence_readiness_instrument_date UNIQUE (instrument_id, as_of_date),
    CONSTRAINT ck_ownership_transformation_sequence_readiness_readiness CHECK (readiness IN (
        'READY', 'INSUFFICIENT_HISTORY', 'MISSING_PREREQUISITE_DATA'
    ))
);

CREATE INDEX ix_ownership_transformation_sequence_readiness_symbol ON ownership.transformation_sequence_readiness (symbol);
