CREATE SCHEMA IF NOT EXISTS reference;

CREATE FUNCTION reference.set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TABLE reference.exchanges (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        text NOT NULL UNIQUE,
    name        text NOT NULL,
    country     text NOT NULL DEFAULT 'IN',
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_exchanges_updated_at
    BEFORE UPDATE ON reference.exchanges
    FOR EACH ROW EXECUTE FUNCTION reference.set_updated_at();

CREATE TABLE reference.sectors (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name              text NOT NULL,
    parent_sector_id  uuid REFERENCES reference.sectors (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_sectors_parent_sector_id ON reference.sectors (parent_sector_id);

CREATE TRIGGER trg_sectors_updated_at
    BEFORE UPDATE ON reference.sectors
    FOR EACH ROW EXECUTE FUNCTION reference.set_updated_at();

CREATE TABLE reference.instruments (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol           text NOT NULL,
    exchange_id      uuid NOT NULL REFERENCES reference.exchanges (id),
    name             text NOT NULL,
    isin             text,
    instrument_type  text NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_instruments_symbol_exchange UNIQUE (symbol, exchange_id)
);

CREATE INDEX ix_instruments_exchange_id ON reference.instruments (exchange_id);

CREATE TRIGGER trg_instruments_updated_at
    BEFORE UPDATE ON reference.instruments
    FOR EACH ROW EXECUTE FUNCTION reference.set_updated_at();

CREATE TABLE reference.trading_calendar (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    exchange_id     uuid NOT NULL REFERENCES reference.exchanges (id),
    calendar_date   date NOT NULL,
    is_trading_day  boolean NOT NULL DEFAULT true,
    description     text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_trading_calendar_exchange_date UNIQUE (exchange_id, calendar_date)
);

CREATE INDEX ix_trading_calendar_exchange_id ON reference.trading_calendar (exchange_id);
