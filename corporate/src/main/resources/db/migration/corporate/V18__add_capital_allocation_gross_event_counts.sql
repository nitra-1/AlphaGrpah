-- Adds true gross entering/exiting event counts alongside the existing net `change` column on
-- corporate.transformation_evidence, so Capital Allocation Stage 3 sequence detection
-- (CapitalAllocationTransformationSequenceEngine) can read a real entering-event count instead of
-- inferring one from Math.max(change, 0) - which misses a real event when its entry and an
-- unrelated exit land on the same day and net to change = 0 (see that engine's javadoc for the
-- full derivation: change(d) = (# actions with exDate == d) - (# actions with exDate == d-180)).
--
-- entered_event_count(d) = # actions with exDate == d (the first term above, alone).
-- exited_event_count(d)  = # actions with exDate == d - window_days (the second term above, alone).
-- change = entered_event_count - exited_event_count always holds, by construction in
-- CapitalAllocationEngine.calculate and enforced by CapitalAllocationEvidenceObservation's
-- compact constructor - hence the CHECK constraint below.
--
-- value/prior_value/change are left untouched for compatibility - this is additive only.
--
-- Backfill limitation (forward-only fix, not a rebuild): entered/exited cannot be deterministically
-- recovered from already-persisted rows, because a historical change = 0 row is genuinely
-- ambiguous - it could mean "nothing happened" or "an entry and an exit cancelled" (the exact gap
-- this migration closes going forward), and corporate.transformation_evidence does not retain
-- enough information to tell those apart after the fact without re-deriving from
-- corporate.corporate_actions. Rows written before this migration are backfilled using the same
-- net-inference approximation Stage 3 used to rely on (entered = GREATEST(change, 0), exited =
-- GREATEST(-change, 0)) purely so the NOT NULL/CHECK constraints hold - those historical rows may
-- still undercount a same-day cancellation exactly like the old Math.max(change, 0) walk did. Only
-- rows computed by CapitalAllocationEngine from this migration onward carry a true gross count.
-- As of this migration there is zero real BUYBACK/RIGHTS corporate-action data in the live
-- database, so no production row is actually affected by this approximation today.
ALTER TABLE corporate.transformation_evidence
    ADD COLUMN entered_event_count integer,
    ADD COLUMN exited_event_count  integer;

UPDATE corporate.transformation_evidence
SET entered_event_count = GREATEST(change, 0),
    exited_event_count  = GREATEST(-change, 0)
WHERE entered_event_count IS NULL;

ALTER TABLE corporate.transformation_evidence
    ALTER COLUMN entered_event_count SET NOT NULL,
    ALTER COLUMN exited_event_count SET NOT NULL,
    ADD CONSTRAINT ck_corporate_transformation_evidence_event_counts_non_negative
        CHECK (entered_event_count >= 0 AND exited_event_count >= 0),
    ADD CONSTRAINT ck_corporate_transformation_evidence_change_equals_net_events
        CHECK (change = entered_event_count - exited_event_count);
