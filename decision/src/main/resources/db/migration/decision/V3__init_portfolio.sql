-- Module 3.3: Portfolio Dashboard. Single global portfolio (same standing decision as the
-- watchlist, V2) - current holdings only, not a transaction ledger: quantity and avg_buy_price
-- are the weighted-average cost basis after every buy, recalculated in decision.portfolio.
-- PortfolioService, never stored as individual buy/sell events - that's Module 3.8's (Trade
-- Journal) job. A holding's row is deleted once its quantity reaches zero (current-position
-- semantics), not kept as a zeroed-out row.
CREATE TABLE decision.portfolio_holdings (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id uuid NOT NULL,
    symbol        varchar(20) NOT NULL,
    quantity      numeric(15,4) NOT NULL,
    avg_buy_price numeric(12,4) NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_portfolio_holdings_instrument UNIQUE (instrument_id),
    CONSTRAINT ck_portfolio_holdings_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_portfolio_holdings_avg_buy_price_positive CHECK (avg_buy_price > 0)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_portfolio_holdings_instrument_id ON decision.portfolio_holdings (instrument_id);

CREATE TRIGGER trg_portfolio_holdings_updated_at
    BEFORE UPDATE ON decision.portfolio_holdings
    FOR EACH ROW EXECUTE FUNCTION common.set_updated_at();
