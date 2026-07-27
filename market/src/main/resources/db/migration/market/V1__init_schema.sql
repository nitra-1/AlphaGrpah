CREATE SCHEMA IF NOT EXISTS market;

CREATE FUNCTION market.set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- Market cap is deliberately not a column here: none of NSE's daily bhavcopy/delivery reports
-- carry it directly, and deriving it from the security master's issued-capital field requires
-- confirming units/semantics we're not confident of yet (Module 1.1). Add it via a migration
-- once a real source exists rather than shipping a column that's always null.
CREATE TABLE market.daily_prices (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id        uuid NOT NULL,
    trade_date           date NOT NULL,
    open_price           numeric NOT NULL,
    high_price           numeric NOT NULL,
    low_price            numeric NOT NULL,
    close_price          numeric NOT NULL,
    volume               bigint NOT NULL,
    delivery_percentage  numeric(5, 2),
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_daily_prices_instrument_date UNIQUE (instrument_id, trade_date)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_daily_prices_instrument_id ON market.daily_prices (instrument_id);
CREATE INDEX ix_daily_prices_trade_date ON market.daily_prices (trade_date);

CREATE TRIGGER trg_daily_prices_updated_at
    BEFORE UPDATE ON market.daily_prices
    FOR EACH ROW EXECUTE FUNCTION market.set_updated_at();
