-- DiscoveryConfirmationEngine used to score every post-anchor session it was handed (up to 10),
-- not just the first 5 - `frozen` was only a label. Rows computed after session 5 therefore carried
-- a drifting score, sometimes a different state (e.g. SWIGGY flipped PARTIALLY_CONFIRMED ->
-- PENDING at session 6), and sessions_elapsed up to 10 ("T+10 of 5" in the UI). The engine now
-- stops at 5 sessions (see DiscoveryConfirmationEngine), but a symbol whose last deal is older than
-- the 20-day interpretation window is never recomputed again, so its final stored row would keep
-- the wrong values forever.
--
-- The correct result for those rows already exists: the first row at exactly 5 sessions for the
-- same (symbol, anchor) was computed before the bug could matter, and every later day with the
-- same anchor would have produced identical confirmation values under the fixed engine (same
-- sessions, same baseline, same deals - SWIGGY's rows at sessions 5 are 58.95 on five consecutive
-- days). So copy that snapshot's confirmation fields onto the later rows.
--
-- Deliberately NOT touched: `confidence` (its confirmation weight is data-coverage, unchanged by
-- this) and the persisted reason codes - both stay as originally computed. Rows with no 5-session
-- snapshot for their anchor (the first interpretation for that anchor already had more than 5
-- sessions) are left alone; nothing in the data can reconstruct them.
UPDATE ownership.institutional_interpretations i
SET discovery_confirmation_state = f.discovery_confirmation_state,
    confirmation_frozen = f.confirmation_frozen,
    confirmation_sessions_elapsed = f.confirmation_sessions_elapsed,
    confirmation_score = f.confirmation_score,
    price_confirmation_score = f.price_confirmation_score,
    delivery_confirmation_score = f.delivery_confirmation_score,
    volume_confirmation_score = f.volume_confirmation_score,
    repeat_activity_confirmation_score = f.repeat_activity_confirmation_score,
    confirmation_coverage_pct = f.confirmation_coverage_pct
FROM (
    SELECT DISTINCT ON (symbol, event_anchor_date)
           symbol, event_anchor_date, discovery_confirmation_state, confirmation_frozen,
           confirmation_sessions_elapsed, confirmation_score, price_confirmation_score,
           delivery_confirmation_score, volume_confirmation_score, repeat_activity_confirmation_score,
           confirmation_coverage_pct
    FROM ownership.institutional_interpretations
    WHERE confirmation_sessions_elapsed = 5
    ORDER BY symbol, event_anchor_date, as_of_date
) f
WHERE i.symbol = f.symbol
  AND i.event_anchor_date = f.event_anchor_date
  AND i.confirmation_sessions_elapsed > 5;
