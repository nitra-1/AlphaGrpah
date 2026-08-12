CREATE SCHEMA IF NOT EXISTS learning;

-- Phase 4A.1: Decision Snapshot Archive. A permanent, append-only copy of what
-- decision.decision_scores showed for one instrument on one day - deliberately separate from
-- that table because DecisionScoreStore.write() upserts ON CONFLICT (instrument_id, as_of_date)
-- DO UPDATE, so a same-day re-run silently overwrites a day's row there. This table never does
-- that (writers must use ON CONFLICT DO NOTHING) - once a day is captured, it stays exactly as
-- captured, which is the whole point of an archive Phase 4B's eventual pattern mining can trust.
CREATE TABLE learning.decision_snapshots (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id         uuid NOT NULL,
    symbol                varchar(20) NOT NULL,
    as_of_date            date NOT NULL,
    swing_score           numeric(5, 2) NOT NULL,
    swing_rating          varchar(15) NOT NULL,
    swing_rank            integer,
    long_term_score       numeric(5, 2) NOT NULL,
    long_term_rating      varchar(15) NOT NULL,
    long_term_rank        integer,
    technical_score       numeric(5, 2),
    fundamental_score     numeric(5, 2),
    institutional_score   numeric(5, 2),
    sector_score          numeric(5, 2),
    risk_score            numeric(5, 2),
    corporate_score       numeric(5, 2),
    confidence            numeric(5, 2) NOT NULL,
    rule_set_version      integer NOT NULL,
    decision_computed_at  timestamptz NOT NULL,
    captured_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_decision_snapshots_swing_rating
        CHECK (swing_rating IN ('STRONG_BUY', 'BUY', 'HOLD', 'REDUCE', 'AVOID')),
    CONSTRAINT ck_decision_snapshots_long_term_rating
        CHECK (long_term_rating IN ('STRONG_BUY', 'BUY', 'HOLD', 'REDUCE', 'AVOID')),
    CONSTRAINT ux_decision_snapshots_instrument_date UNIQUE (instrument_id, as_of_date)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_decision_snapshots_instrument_id ON learning.decision_snapshots (instrument_id);
CREATE INDEX ix_decision_snapshots_as_of_date ON learning.decision_snapshots (as_of_date);

-- Phase 4A.2/4.3: Forward Outcome Tracker + Signal Outcome Tracker, one physical table for both -
-- they share the same expensive step (fetching split/bonus-adjusted price history and measuring
-- forward return), so a per-domain-score outcome row is folded into the same row as the composite
-- swing/long-term outcome rather than a second pipeline re-doing the same price lookup.
--
-- One row per (instrument, decision date, horizon) - not every horizon is computable the day
-- after a decision is made, so rows for the same decision appear incrementally as each horizon's
-- trading days actually elapse (5D first, 60D last), never fabricated ahead of time.
CREATE TABLE learning.forward_outcomes (
    id                                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id                     uuid NOT NULL,
    symbol                            varchar(20) NOT NULL,
    as_of_date                        date NOT NULL,
    horizon_days                      smallint NOT NULL,
    outcome_date                      date NOT NULL,
    reference_price                   numeric NOT NULL,
    outcome_price                     numeric NOT NULL,
    forward_return_percentage         numeric(7, 2) NOT NULL,
    swing_rating                      varchar(15) NOT NULL,
    swing_directionally_correct       boolean,
    long_term_rating                  varchar(15) NOT NULL,
    long_term_directionally_correct   boolean,
    technical_signal_correct          boolean,
    fundamental_signal_correct        boolean,
    institutional_signal_correct      boolean,
    sector_signal_correct             boolean,
    risk_signal_correct               boolean,
    corporate_signal_correct          boolean,
    computed_at                       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_forward_outcomes_horizon_days CHECK (horizon_days IN (5, 10, 20, 60)),
    CONSTRAINT ck_forward_outcomes_swing_rating
        CHECK (swing_rating IN ('STRONG_BUY', 'BUY', 'HOLD', 'REDUCE', 'AVOID')),
    CONSTRAINT ck_forward_outcomes_long_term_rating
        CHECK (long_term_rating IN ('STRONG_BUY', 'BUY', 'HOLD', 'REDUCE', 'AVOID')),
    CONSTRAINT ux_forward_outcomes_instrument_date_horizon UNIQUE (instrument_id, as_of_date, horizon_days)
);

CREATE INDEX ix_forward_outcomes_instrument_id ON learning.forward_outcomes (instrument_id);
CREATE INDEX ix_forward_outcomes_as_of_date ON learning.forward_outcomes (as_of_date);
CREATE INDEX ix_forward_outcomes_swing_rating_horizon ON learning.forward_outcomes (swing_rating, horizon_days);
