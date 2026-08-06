-- Module 3.8: Trade Journal. Auto-recorded as a byproduct of every real
-- decision.portfolio.PortfolioService.buy/sell call (option 1 - not a separate manual-entry
-- surface, avoiding double entry of the same trade). Immutable - one row per executed trade,
-- never revised, the same Layer-1 pattern corporate.management_observations etc. established.
--
-- cost_basis_price/realized_pnl are set only for SELL rows: cost_basis_price is the position's
-- weighted-average cost basis at the exact moment of the sell (captured before the sell mutates
-- it), so realized_pnl = quantity * (price - cost_basis_price) is self-explanatory and auditable
-- later without needing portfolio_holdings' own history (which doesn't retain any - it's
-- current-position-only, Module 3.3's own deliberate scope boundary).
CREATE TABLE decision.trade_journal_entries (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id     uuid NOT NULL,
    symbol            varchar(20) NOT NULL,
    action            varchar(4) NOT NULL,
    quantity          numeric(15,4) NOT NULL,
    price             numeric(12,4) NOT NULL,
    cost_basis_price  numeric(12,4),
    realized_pnl      numeric(18,4),
    rationale         text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_trade_journal_entries_action CHECK (action IN ('BUY', 'SELL')),
    CONSTRAINT ck_trade_journal_entries_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_trade_journal_entries_price_positive CHECK (price > 0)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_trade_journal_entries_instrument_id ON decision.trade_journal_entries (instrument_id);
CREATE INDEX ix_trade_journal_entries_created_at ON decision.trade_journal_entries (created_at);
