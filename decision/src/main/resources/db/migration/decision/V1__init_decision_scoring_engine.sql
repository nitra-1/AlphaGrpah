-- Module 3.1: Scoring/Decision Engine - the roadmap's original Phase 1 module that was never
-- built. Blends the six existing domain scores (Technical, Fundamental, Institutional, Sector,
-- Risk, Corporate) into two horizon-specific composites: Swing Score (short-term trading) and
-- Long-Term Score (investment horizon). One row per (instrument, day), same Layer-2-snapshot
-- shape every prior engine uses.
--
-- swing_rank/long_term_rank are nullable and filled in by a second pass
-- (decision.engine.DecisionScoringOrchestrator) after every instrument's scores for the day are
-- computed - ranking is a cross-instrument concern the single-instrument
-- decision.engine.DecisionScoringEngine cannot know on its own.
--
-- The six domain sub-scores are carried alongside the composites (nullable - an instrument
-- missing a given domain simply doesn't contribute to that domain's weight, see
-- DecisionScoringEngine's renormalization) so a future consumer (e.g. the AI Analyst, extended in
-- a later Phase 3 module to explain Rank changes) can cite which domain drove a change, not just
-- the final number - the same carried-sub-scores pattern corporate.corporate_scores established.
CREATE SCHEMA IF NOT EXISTS decision;

CREATE TABLE decision.decision_scores (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id       uuid NOT NULL,
    symbol              varchar(20) NOT NULL,
    as_of_date          date NOT NULL,
    swing_score         numeric(5,2) NOT NULL,
    swing_rating        varchar(15) NOT NULL,
    swing_rank          integer,
    long_term_score     numeric(5,2) NOT NULL,
    long_term_rating    varchar(15) NOT NULL,
    long_term_rank      integer,
    technical_score     numeric(5,2),
    fundamental_score   numeric(5,2),
    institutional_score numeric(5,2),
    sector_score        numeric(5,2),
    risk_score          numeric(5,2),
    corporate_score     numeric(5,2),
    confidence          numeric(5,2) NOT NULL,
    rule_set_version    integer NOT NULL,
    computed_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_decision_scores_swing_rating CHECK (swing_rating IN ('STRONG_BUY', 'BUY', 'HOLD', 'REDUCE', 'AVOID')),
    CONSTRAINT ck_decision_scores_long_term_rating CHECK (long_term_rating IN ('STRONG_BUY', 'BUY', 'HOLD', 'REDUCE', 'AVOID')),
    CONSTRAINT ux_decision_scores_instrument_date UNIQUE (instrument_id, as_of_date)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_decision_scores_instrument_id ON decision.decision_scores (instrument_id);
CREATE INDEX ix_decision_scores_as_of_date ON decision.decision_scores (as_of_date);
CREATE INDEX ix_decision_scores_swing_rank ON decision.decision_scores (as_of_date, swing_rank);
CREATE INDEX ix_decision_scores_long_term_rank ON decision.decision_scores (as_of_date, long_term_rank);
