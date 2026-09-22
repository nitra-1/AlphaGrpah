-- Sector Stage 3 sequence detection (docs/008_Stage3_Sequence_Detection_Specification.md §14.5):
-- converts sector.inflection_states/_state_reasons history into ordered transformation patterns.
-- One row per (instrument, as_of_date, sequence_type) - an instrument may legitimately have
-- multiple simultaneous sequences. Mirrors market.transformation_sequences (V7) exactly except
-- schema-qualified for sector, sequence_type values, and history_sessions (Sector is a daily
-- family like Market, not quarterly like Ownership/Financial).
--
-- No driving_metric/level/change/velocity_band columns - a sequence's "evidence" is its own step
-- history (sector.transformation_sequence_reasons), not a single banded numeric metric.
CREATE TABLE sector.transformation_sequences (
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
    CONSTRAINT ux_sector_transformation_sequences_instrument_date_type UNIQUE (instrument_id, as_of_date, sequence_type),
    CONSTRAINT ck_sector_transformation_sequences_type CHECK (sequence_type IN (
        'SECTOR_TAILWIND_SEQUENCE', 'STOCK_LEADERSHIP_EMERGENCE', 'IDIOSYNCRATIC_LEADERSHIP'
    )),
    CONSTRAINT ck_sector_transformation_sequences_phase CHECK (sequence_phase IN (
        'FORMING', 'PROGRESSING', 'COMPLETE', 'BROKEN'
    ))
);

CREATE INDEX ix_sector_transformation_sequences_symbol ON sector.transformation_sequences (symbol);

-- Deleted and reinserted on every recompute of the parent row, matching every Stage 2/3 reasons
-- table's exact convention.
CREATE TABLE sector.transformation_sequence_reasons (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_id        uuid NOT NULL REFERENCES sector.transformation_sequences (id) ON DELETE CASCADE,
    reason_code        varchar(60) NOT NULL,
    metric_value       numeric(18, 4) NULL,
    evidence_reference text NULL
);

CREATE INDEX ix_sector_transformation_sequence_reasons_sequence ON sector.transformation_sequence_reasons (sequence_id);

-- One row per (instrument, as_of_date) - NOT per sequence_type - written every real run for every
-- instrument with any real Sector Stage 1 evidence, independent of whether any sequence fired.
-- history_sessions is the real row count found in the fetched lookback window.
CREATE TABLE sector.transformation_sequence_readiness (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id     uuid NOT NULL,
    symbol            text NOT NULL,
    as_of_date        date NOT NULL,
    history_sessions  integer NOT NULL,
    readiness         varchar(30) NOT NULL,
    computed_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_sector_transformation_sequence_readiness_instrument_date UNIQUE (instrument_id, as_of_date),
    CONSTRAINT ck_sector_transformation_sequence_readiness_readiness CHECK (readiness IN (
        'READY', 'INSUFFICIENT_HISTORY', 'MISSING_PREREQUISITE_DATA'
    ))
);

CREATE INDEX ix_sector_transformation_sequence_readiness_symbol ON sector.transformation_sequence_readiness (symbol);
