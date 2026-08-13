-- Learning Readiness Hardening: forward_outcomes is a DERIVED measurement, not an immutable fact
-- like decision_snapshots - PriceAdjustmentService.adjustedHistory() is deliberately stateless and
-- always live (its own javadoc: an adjustment factor for a historical date changes retroactively
-- the moment a later BONUS/SPLIT is ingested), but this table was write-once (ON CONFLICT DO
-- NOTHING) with nothing to detect or correct a now-stale row. status/price_adjustment_watermark
-- let ForwardOutcomeInvalidator flag a row whose adjusted-price basis has since changed, and
-- recomputed_at records when a correction actually happened, distinct from the original computed_at.
ALTER TABLE learning.forward_outcomes
    ADD COLUMN status varchar(15) NOT NULL DEFAULT 'CURRENT',
    ADD COLUMN price_adjustment_watermark timestamptz,
    ADD COLUMN recomputed_at timestamptz,
    ADD CONSTRAINT ck_forward_outcomes_status CHECK (status IN ('CURRENT', 'INVALIDATED', 'RECOMPUTING'));

CREATE INDEX ix_forward_outcomes_status ON learning.forward_outcomes (status);
