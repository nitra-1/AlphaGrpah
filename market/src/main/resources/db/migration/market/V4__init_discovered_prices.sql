-- Sprint 2 of the bulk/block deal auto-discovery roadmap: market.daily_prices is keyed strictly
-- by instrument_id and the daily bhavdata pipeline discards any row for a symbol not in
-- reference.instruments - the exact same fate bulk/block deals had before Sprint 1's
-- ownership.discovered_deals rescued them, except nothing rescued the price side. Without this,
-- a 20-trading-day ADTV for a Discovery candidate is not computable anywhere.
--
-- Symbol-keyed (text), not instrument_id-keyed - these are, by definition, untracked symbols with
-- no row in reference.instruments. Capture is gated to genuine Discovery candidates only (see
-- BhavdataNormalizer/DiscoveryCandidateLookup) - not every untracked NSE symbol in the daily
-- bhavdata file, which would be real, unbounded scope creep.
--
-- daily_traded_value is NSE's own real turnover (TURNOVER_LACS * 100000, real rupees) - genuine
-- exchange-reported turnover, not a close * volume estimate. Nullable since a malformed/blank
-- turnover field shouldn't block capturing the rest of a row's real OHLCV data.
CREATE TABLE market.discovered_prices (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol               text NOT NULL,
    trade_date           date NOT NULL,
    open_price           numeric NOT NULL,
    high_price           numeric NOT NULL,
    low_price            numeric NOT NULL,
    close_price          numeric NOT NULL,
    volume               bigint NOT NULL,
    daily_traded_value   numeric(18, 2),
    delivery_percentage  numeric(5, 2),
    ingested_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_discovered_prices_symbol_date UNIQUE (symbol, trade_date)
);

CREATE INDEX ix_discovered_prices_symbol ON market.discovered_prices (symbol);
