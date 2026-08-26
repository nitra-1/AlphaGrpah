-- Sprint 1 of the bulk/block deal auto-discovery roadmap: a bulk/block deal whose symbol isn't
-- in reference.instruments used to just vanish (quarantined by Pipeline's per-record catch as
-- "Unknown instrument", never persisted anywhere). NSE's real daily bulk/block feed concentrates
-- in small/micro-cap and recently-listed names - genuine institutional-buying signal in stocks
-- AlphaGraph doesn't track yet, discarded every single day. This captures it instead, for admin
-- review via the Discovery page. ownership.bulk_deals itself is untouched - this is purely
-- additive.
--
-- Two tables, not one, because "discovery status" is a property of the SYMBOL, not of any single
-- deal event: a symbol can have many deals across many dates, and Discard/Promote decisions apply
-- to the symbol as a whole, not to one specific row.

-- One row per real rejected deal - the append-only raw log. security_name is nullable (display
-- only, never used for matching) since a malformed/short CSV row could plausibly lack it even
-- though real NSE data always carries one. No instrument_id - by definition, these are the deals
-- that couldn't resolve to one. deal_value is stored (quantity * price) rather than computed on
-- every read, since the Discovery review list aggregates it per symbol.
CREATE TABLE ownership.discovered_deals (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol         text NOT NULL,
    security_name  text,
    deal_date      date NOT NULL,
    client_name    text NOT NULL,
    buy_sell       varchar(4) NOT NULL,
    quantity       bigint NOT NULL,
    price          numeric(12, 2) NOT NULL,
    deal_value     numeric(18, 2) NOT NULL,
    deal_type      varchar(5) NOT NULL,
    source         text NOT NULL DEFAULT 'NSE_BULK_BLOCK',
    ingested_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_discovered_deals_buy_sell CHECK (buy_sell IN ('BUY', 'SELL')),
    CONSTRAINT ck_discovered_deals_deal_type CHECK (deal_type IN ('BULK', 'BLOCK')),
    CONSTRAINT ux_discovered_deals_natural_key UNIQUE (symbol, deal_date, client_name, buy_sell, deal_type)
);

CREATE INDEX ix_discovered_deals_symbol ON ownership.discovered_deals (symbol);

-- One row per discovered symbol - the admin's decision, separate from the raw deal log above.
-- "PROMOTED" is deliberately never written here (Sprint 1 detects promotion live, by checking
-- whether the symbol now exists in reference.instruments, so InstrumentAdditionService stays
-- completely unaware of Discovery) - the value is defined on the CHECK constraint for schema
-- completeness / a possible future reconciliation job, not written by anything yet. Same for
-- REVIEWED - Sprint 1's UI only ever writes NEW (on first capture) or DISMISSED (Discard action).
CREATE TABLE ownership.discovery_status (
    symbol            text PRIMARY KEY,
    status            varchar(10) NOT NULL DEFAULT 'NEW',
    first_detected_at timestamptz NOT NULL DEFAULT now(),
    last_detected_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_discovery_status_status CHECK (status IN ('NEW', 'REVIEWED', 'PROMOTED', 'DISMISSED'))
);
