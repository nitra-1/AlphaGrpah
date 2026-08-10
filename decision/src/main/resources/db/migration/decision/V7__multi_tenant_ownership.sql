-- Multi-tenancy: Portfolio (V3), Watchlist (V2), and Trade Journal (V5) were all explicitly
-- disclosed as single-global-state at the time - V2's own comment says so outright ("Single
-- global list, not per-user - api.platform_users only has ADMIN/SYSTEM roles, there is no
-- investor/renter-style external user model yet... deferred"). api V3 just added that user
-- model (a real USER role); this migration is what actually uses it.
--
-- user_id references api.platform_users by value only, no cross-schema foreign key - same
-- established convention as instrument_id referencing reference.instruments
-- (docs/003_Database_Architecture.md §2). Existing rows (all created under the old single-tenant
-- model) are backfilled to the seeded admin account so nothing is silently orphaned or dropped -
-- the admin's existing portfolio/watchlist/journal history becomes "admin's own", not lost.

ALTER TABLE decision.portfolio_holdings ADD COLUMN user_id uuid;
ALTER TABLE decision.watchlist_items ADD COLUMN user_id uuid;
ALTER TABLE decision.trade_journal_entries ADD COLUMN user_id uuid;

UPDATE decision.portfolio_holdings SET user_id = (SELECT id FROM api.platform_users WHERE email = 'admin@alphagraph.local');
UPDATE decision.watchlist_items SET user_id = (SELECT id FROM api.platform_users WHERE email = 'admin@alphagraph.local');
UPDATE decision.trade_journal_entries SET user_id = (SELECT id FROM api.platform_users WHERE email = 'admin@alphagraph.local');

ALTER TABLE decision.portfolio_holdings ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE decision.watchlist_items ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE decision.trade_journal_entries ALTER COLUMN user_id SET NOT NULL;

-- "One holding of a stock, platform-wide" becomes "one holding of a stock, per user".
ALTER TABLE decision.portfolio_holdings DROP CONSTRAINT ux_portfolio_holdings_instrument;
ALTER TABLE decision.portfolio_holdings ADD CONSTRAINT ux_portfolio_holdings_user_instrument UNIQUE (user_id, instrument_id);

ALTER TABLE decision.watchlist_items DROP CONSTRAINT ux_watchlist_items_instrument;
ALTER TABLE decision.watchlist_items ADD CONSTRAINT ux_watchlist_items_user_instrument UNIQUE (user_id, instrument_id);

CREATE INDEX ix_portfolio_holdings_user_id ON decision.portfolio_holdings (user_id);
CREATE INDEX ix_watchlist_items_user_id ON decision.watchlist_items (user_id);
CREATE INDEX ix_trade_journal_entries_user_id ON decision.trade_journal_entries (user_id);
