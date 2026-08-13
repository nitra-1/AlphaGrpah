-- Learning Readiness Hardening: mirrors decision.decision_scores' V9 provenance columns onto the
-- immutable archive, so the same per-domain as-of-date/rule-set-version/computed-at facts survive
-- into learning.decision_snapshots exactly as captured - not just the blended composite. All
-- nullable, same as the source columns; existing archived rows predate this and have no
-- provenance to backfill.
ALTER TABLE learning.decision_snapshots
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
