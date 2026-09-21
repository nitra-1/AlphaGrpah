-- Fixes the real, previously-disclosed Ownership Stage 2 as_of_date cadence bug (task_b3c0547c,
-- flagged and deferred during Financial's Stage 2 build): OwnershipTransformationEngine used
-- Clock.now() instead of a real information-availability date for as_of_date, so every calendar
-- day the job ran minted a brand-new row for the exact same real quarterly transition. Confirmed
-- live before this migration: ownership.transformation_states had 236 total rows but only 59 real
-- distinct (instrument_id, latest_period_end) combinations - the other 177 rows are pure
-- duplication artifacts, one per calendar day the job happened to run.
--
-- The code fix (OwnershipTransformationEngine) now uses TransformationShareholdingPeriod's own
-- availableFrom(drivingMetric): shareholding_pattern.created_at for PROMOTER/PUBLIC (real from the
-- live summary JSON) or NO_CLEAR_SIGNAL, shareholding_pattern.xbrl_enriched_at for the other 7
-- metrics (real only once XBRL enrichment runs) - never period_end itself, which describes which
-- quarter the data is ABOUT, never when it actually became known. This migration applies the exact
-- same rule to the rows already written under the old, buggy behavior: for each real
-- (instrument_id, latest_period_end), keeps only the most-recently-computed row, corrects its
-- as_of_date to the real availability date, and removes the duplicates (reasons cascade-delete via
-- the existing FK). Safe/no-op on any environment that never had this bug manifest - the dedup
-- WHERE clause only ever matches real duplicates, and the corrective UPDATE only ever touches rows
-- this migration itself identifies as survivors.

-- Duplicates must be deleted BEFORE the survivor's as_of_date is corrected: the corrected date is
-- a real shareholding_pattern.created_at/xbrl_enriched_at value, which can legitimately coincide
-- with one of the old Clock-stamped as_of_date values still sitting on a not-yet-deleted sibling
-- row for the same instrument (confirmed live: DRREDDY's corrected date landed on 2026-09-16, one
-- of its own duplicate rows' pre-fix as_of_date) - updating first would collide with
-- ux_transformation_states_instrument_date before that sibling is gone.
DELETE FROM ownership.transformation_states
WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY instrument_id, latest_period_end ORDER BY computed_at DESC) AS rn
        FROM ownership.transformation_states
    ) ranked
    WHERE rn > 1
);

WITH corrected AS (
    SELECT ts.id,
           CASE
               WHEN ts.driving_metric IS NULL OR ts.driving_metric IN ('PROMOTER', 'PUBLIC')
                   THEN sp.created_at AT TIME ZONE 'Asia/Kolkata'
               ELSE COALESCE(sp.xbrl_enriched_at, sp.created_at) AT TIME ZONE 'Asia/Kolkata'
           END::date AS available_from
    FROM ownership.transformation_states ts
    JOIN ownership.shareholding_pattern sp
        ON sp.instrument_id = ts.instrument_id AND sp.period_end = ts.latest_period_end
)
UPDATE ownership.transformation_states ts
SET as_of_date = corrected.available_from
FROM corrected
WHERE ts.id = corrected.id;

-- Self-check: fail loudly rather than silently leave duplicates or a miscount behind.
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
