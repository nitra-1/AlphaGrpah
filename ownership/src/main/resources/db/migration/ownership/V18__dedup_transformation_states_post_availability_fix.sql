-- Follow-up to V17. V17 fixed as_of_date to reflect real information availability instead of
-- Clock.now(), but the writer's upsert key remains (instrument_id, as_of_date), not
-- (instrument_id, latest_period_end). Confirmed live: when XBRL enrichment legitimately advances
-- the driving metric within the same quarter (e.g. ADANIPORTS/HDFCBANK moved from
-- PROMOTER/no-driving-metric on 2026-09-16 to FII/DII on 2026-09-21 once real xbrl_enriched_at
-- data arrived), as_of_date correctly advances too, but the writer inserts a new row alongside the
-- old one instead of replacing it - reintroducing multi-row-per-quarter duplication.
--
-- This migration applies the same one-time cleanup V17 did (keep the most-recently-computed row
-- per (instrument_id, latest_period_end), drop the rest) without changing as_of_date, since every
-- surviving row here already has a correct availability-derived date from V17/the code fix.
-- Deliberately generic, not scoped to specific instrument ids, so it is a safe no-op wherever this
-- has not (yet) recurred.
--
-- The underlying upsert-key redesign - (instrument_id, latest_period_end) as the real key, with
-- as_of_date as a plain column that can advance in place within a quarter - is a separate, larger
-- decision (new unique constraint, writer changes, re-verification of every downstream reader) and
-- is explicitly deferred to a later task, not bundled into this fix.

DELETE FROM ownership.transformation_states
WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY instrument_id, latest_period_end ORDER BY computed_at DESC) AS rn
        FROM ownership.transformation_states
    ) ranked
    WHERE rn > 1
);

DO $$
DECLARE
    total_rows integer;
    distinct_combinations integer;
BEGIN
    SELECT count(*) INTO total_rows FROM ownership.transformation_states;
    SELECT count(DISTINCT (instrument_id, latest_period_end)) INTO distinct_combinations FROM ownership.transformation_states;
    IF total_rows <> distinct_combinations THEN
        RAISE EXCEPTION 'ownership.transformation_states dedup failed: % total rows but % distinct (instrument_id, latest_period_end) combinations', total_rows, distinct_combinations;
    END IF;
END $$;
