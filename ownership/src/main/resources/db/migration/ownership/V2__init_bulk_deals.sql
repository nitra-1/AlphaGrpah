-- Module 1.7 (Institutional Engine): real, live NSE bulk/block deals data. Unlike
-- shareholding_pattern, this IS a genuine free public bulk source (archives.nseindia.com/content/
-- equities/bulk.csv and .../block.csv) - but with a real constraint the collector's javadoc
-- covers: the URL carries no date parameter, so it only ever exposes the CURRENT day's deals,
-- never a historical archive. Real history accumulates day-by-day from here on, same as
-- market.daily_prices before Module 1.5's backfill.
--
-- deal_type distinguishes which of the two real NSE reports a row came from - bulk deals (single
-- trade >= 0.5% of a company's shares) and block deals (large trades in a special window,
-- >= 5 lakh shares or >= INR 5 crore) are reported as two separate files with near-identical
-- shape, so one table covers both.
CREATE TABLE ownership.bulk_deals (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id  uuid NOT NULL,
    deal_date      date NOT NULL,
    client_name    text NOT NULL,
    buy_sell       varchar(4) NOT NULL,
    quantity       bigint NOT NULL,
    price          numeric(12, 2) NOT NULL,
    deal_type      varchar(5) NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_bulk_deals_buy_sell CHECK (buy_sell IN ('BUY', 'SELL')),
    CONSTRAINT ck_bulk_deals_deal_type CHECK (deal_type IN ('BULK', 'BLOCK')),
    CONSTRAINT ux_bulk_deals_natural_key UNIQUE (instrument_id, deal_date, client_name, buy_sell, deal_type)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_bulk_deals_instrument_id ON ownership.bulk_deals (instrument_id);
CREATE INDEX ix_bulk_deals_deal_date ON ownership.bulk_deals (deal_date);

CREATE TRIGGER trg_bulk_deals_updated_at
    BEFORE UPDATE ON ownership.bulk_deals
    FOR EACH ROW EXECUTE FUNCTION ownership.set_updated_at();
