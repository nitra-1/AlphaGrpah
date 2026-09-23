-- Capital Allocation Stage 3 sequence detection (docs/008_Stage3_Sequence_Detection_Specification.md
-- §14.4): converts corporate.transformation_evidence's own rolling-180-day event-count history into
-- REPEATED_CAPITAL_RETURN/REPEATED_EQUITY_RAISE occurrence clusters. Unlike every prior domain,
-- this reads Stage 1 evidence directly (CapitalAllocationEvidenceReader.findHistory per metric),
-- never corporate.inflection_states - see CapitalAllocationTransformationSequenceEngine's own
-- javadoc for why a Stage 2 history reader would actively lose signal here (the MIXED-state
-- tie-break only records the winning metric's own change per day).
--
-- current_step/total_steps are a repeat count, not a chain position: total_steps is the configured
-- required-occurrence threshold (e.g. 2), current_step is min(observed_occurrences, total_steps).
-- observed_occurrences is the uncapped real count of qualifying occurrences in the active cluster -
-- a genuinely new corporate action entering the 180-day window (change > 0 on the underlying
-- metric), never mere window persistence (value > 0 alone) - added so a future Stage 4 can
-- distinguish "just reached the threshold" from "a serial repeat offender" without touching the
-- shared phase taxonomy. Because change is a NET figure (entries minus exits - see the engine's
-- javadoc for the disclosed same-day-cancellation limitation), observed_occurrences is really the
-- minimum number of entries inferable from positive net changes, not a guaranteed exact event
-- count - always a conservative undercount, never an overcount.
--
-- Once COMPLETE, a cluster does NOT reset to FORMING when a further occurrence arrives inside the
-- repeat window - it stays COMPLETE and last_step_date simply refreshes. It only closes (BROKEN)
-- once genuinely silent for longer than its own repeat window since the last real occurrence -
-- otherwise a buyback pair from years ago would report as "currently active" forever.
CREATE TABLE corporate.transformation_sequences (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id        uuid NOT NULL,
    symbol               text NOT NULL,
    as_of_date           date NOT NULL,
    sequence_type        varchar(60) NOT NULL,
    sequence_phase       varchar(20) NOT NULL,
    current_step         integer NOT NULL,
    total_steps          integer NOT NULL,
    observed_occurrences integer NOT NULL DEFAULT 0,
    first_step_date      date NULL,
    last_step_date       date NULL,
    sequence_strength    numeric(5, 2) NOT NULL,
    confidence           numeric(5, 2) NOT NULL,
    rule_version         integer NOT NULL DEFAULT 1,
    computed_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_corporate_transformation_sequences_instrument_date_type UNIQUE (instrument_id, as_of_date, sequence_type),
    CONSTRAINT ck_corporate_transformation_sequences_type CHECK (sequence_type IN (
        'REPEATED_CAPITAL_RETURN', 'REPEATED_EQUITY_RAISE'
    )),
    CONSTRAINT ck_corporate_transformation_sequences_phase CHECK (sequence_phase IN (
        'FORMING', 'PROGRESSING', 'COMPLETE', 'BROKEN'
    ))
);

CREATE INDEX ix_corporate_transformation_sequences_symbol ON corporate.transformation_sequences (symbol);

-- Deleted and reinserted on every recompute of the parent row, matching every Stage 2/3 reasons
-- table's exact convention.
CREATE TABLE corporate.transformation_sequence_reasons (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_id        uuid NOT NULL REFERENCES corporate.transformation_sequences (id) ON DELETE CASCADE,
    reason_code        varchar(60) NOT NULL,
    metric_value       numeric(18, 4) NULL,
    evidence_reference text NULL
);

CREATE INDEX ix_corporate_transformation_sequence_reasons_sequence ON corporate.transformation_sequence_reasons (sequence_id);

-- One row per (instrument, as_of_date) - NOT per sequence_type - a single combined readiness
-- verdict is sufficient since both sequence types share the same 180-day repeat window (see the
-- engine's evaluateReadiness javadoc). history_days is real calendar-day coverage
-- (latest evidence date - earliest evidence date) of the LEAST-observed metric that actually has
-- evidence, not evidence-row count - a company observed for 3 days is not meaningfully more ready
-- than 2, so row count would be a misleading signal here. Named history_days (not
-- history_sessions/history_periods like the daily/quarterly domains) because this is neither.
CREATE TABLE corporate.transformation_sequence_readiness (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id     uuid NOT NULL,
    symbol            text NOT NULL,
    as_of_date        date NOT NULL,
    history_days      integer NOT NULL,
    readiness         varchar(30) NOT NULL,
    computed_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_corporate_transformation_sequence_readiness_instrument_date UNIQUE (instrument_id, as_of_date),
    CONSTRAINT ck_corporate_transformation_sequence_readiness_readiness CHECK (readiness IN (
        'READY', 'INSUFFICIENT_HISTORY', 'MISSING_PREREQUISITE_DATA'
    ))
);

CREATE INDEX ix_corporate_transformation_sequence_readiness_symbol ON corporate.transformation_sequence_readiness (symbol);
