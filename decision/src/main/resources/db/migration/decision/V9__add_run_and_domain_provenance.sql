-- Learning Readiness Hardening: DecisionScoringOrchestrator.recomputeOne() used to pull each of
-- the six domain scores via an unconditional "latest" lookup, discarding which date/rule-set
-- version each one actually came from. These columns preserve that provenance so a future replay
-- can tell "technical score is from today, fundamental score is from 3 weeks ago, computed under
-- FUND_V2" apart from an undated blend. All nullable - existing rows predate this feature and
-- have no provenance to backfill; that gap is real and disclosed, not hidden.
ALTER TABLE decision.decision_scores
    ADD COLUMN decision_run_id uuid,

    ADD COLUMN technical_score_as_of_date date,
    ADD COLUMN technical_rule_set_version integer,
    ADD COLUMN technical_computed_at timestamptz,

    ADD COLUMN fundamental_score_as_of_date date,
    ADD COLUMN fundamental_rule_set_version integer,
    ADD COLUMN fundamental_computed_at timestamptz,

    ADD COLUMN institutional_score_as_of_date date,
    ADD COLUMN institutional_rule_set_version integer,
    ADD COLUMN institutional_computed_at timestamptz,

    ADD COLUMN sector_score_as_of_date date,
    ADD COLUMN sector_rule_set_version integer,
    ADD COLUMN sector_computed_at timestamptz,

    ADD COLUMN risk_score_as_of_date date,
    ADD COLUMN risk_rule_set_version integer,
    ADD COLUMN risk_computed_at timestamptz,

    ADD COLUMN corporate_score_as_of_date date,
    ADD COLUMN corporate_rule_set_version integer,
    ADD COLUMN corporate_computed_at timestamptz,

    ADD COLUMN swing_rank_universe_size integer,
    ADD COLUMN long_term_rank_universe_size integer;

CREATE INDEX ix_decision_scores_decision_run_id ON decision.decision_scores (decision_run_id);
