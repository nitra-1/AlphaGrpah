-- Multi-tenancy: Portfolio/Watchlist/Trade Journal move from single-global-state to per-account
-- (decision V7). This is the account type real end users get - ADMIN retains every ADMIN-only
-- capability (News Review, Add Instrument, Add Financial Data) *and* still gets their own
-- personal Portfolio/Watchlist like any USER, since role only gates admin-only endpoints, not
-- portfolio/watchlist access.
ALTER TABLE api.platform_users DROP CONSTRAINT platform_users_role_check;
ALTER TABLE api.platform_users ADD CONSTRAINT platform_users_role_check
    CHECK (role = ANY (ARRAY['ADMIN', 'SYSTEM', 'USER']));
