-- Module 3.2: Watchlist. Single global list, not per-user - api.platform_users only has
-- ADMIN/SYSTEM roles, there is no investor/renter-style external user model yet
-- (docs/004_API_Architecture.md §4 explicitly defers that to "Phase 3's portfolio/watchlist
-- features", but the user confirmed a single shared watchlist rather than building that user
-- model now). instrument_id is UNIQUE - an instrument is either on the list or it isn't, no
-- duplicate entries.
CREATE TABLE decision.watchlist_items (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id uuid NOT NULL,
    symbol        varchar(20) NOT NULL,
    added_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_watchlist_items_instrument UNIQUE (instrument_id)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_watchlist_items_instrument_id ON decision.watchlist_items (instrument_id);
