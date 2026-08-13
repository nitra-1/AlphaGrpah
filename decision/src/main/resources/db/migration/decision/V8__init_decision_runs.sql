-- Learning Readiness Hardening: gives learning.decision_snapshots something concrete to check
-- before archiving a day's decision.decision_scores cohort, instead of trusting that the 5-minute
-- gap between DecisionScoringScheduler (21:45 IST) and DecisionSnapshotScheduler (21:50 IST) was
-- always enough. One row per as_of_date (upserted, not inserted fresh on every manual re-run) so
-- it stays in lockstep with decision_scores' own per-day-upsert idempotency model.
CREATE TABLE decision.decision_runs (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    as_of_date        date NOT NULL,
    started_at        timestamptz NOT NULL,
    completed_at      timestamptz,
    status            varchar(20) NOT NULL,
    instrument_count  integer,
    ranked_count      integer,
    rule_set_version  integer NOT NULL,
    CONSTRAINT ck_decision_runs_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ux_decision_runs_as_of_date UNIQUE (as_of_date)
);

CREATE INDEX ix_decision_runs_as_of_date ON decision.decision_runs (as_of_date);
