-- Sprint 2 of the bulk/block deal auto-discovery roadmap: same-participant counting (repetition/
-- breadth inputs to the Deal Materiality Engine, see ownership.deals.BulkDealContextReader) needs
-- a deterministic normalized form of client_name - real NSE bulk/block data reports the exact same
-- institution under slightly different casing/whitespace/punctuation across different deals/days
-- (e.g. "ABC Mutual Fund" vs "ABC MUTUAL FUND"), which would otherwise undercount genuine
-- repeated-participant activity.
--
-- Deliberately a simple, deterministic normalization only (uppercase, trim, strip punctuation,
-- collapse whitespace) - NOT real entity resolution. "ABC MUTUAL FUND LTD" vs "ABC MUTUAL FUND"
-- genuinely won't collapse under this; that needs real entity resolution, out of scope for this
-- sprint and disclosed as a known v1 limitation.
--
-- Computed once at capture time by ownership.deals.DiscoveredDealWriter going forward (same
-- "compute once, don't make every consumer recompute it" precedent as deal_value); backfilled here
-- for rows already captured under Sprint 1, using the identical algorithm in plain SQL so old and
-- new rows are never inconsistently normalized.
ALTER TABLE ownership.discovered_deals ADD COLUMN client_name_normalized text;

UPDATE ownership.discovered_deals
SET client_name_normalized = NULLIF(
    regexp_replace(regexp_replace(upper(trim(client_name)), '[^A-Z0-9 ]', '', 'g'), '\s+', ' ', 'g'),
    ''
)
WHERE client_name_normalized IS NULL;

CREATE INDEX ix_discovered_deals_client_name_normalized ON ownership.discovered_deals (client_name_normalized);
