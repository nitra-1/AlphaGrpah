CREATE SCHEMA IF NOT EXISTS financial;

CREATE FUNCTION financial.set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- Same real-world gap as ownership.shareholding_pattern (Module 1.2): NSE/BSE don't publish
-- financial results in a free bulk format - they're filed per-company as XBRL/PDF, with bulk
-- access behind paid data subscriptions. This module ships against a manually-compiled sample
-- (real researched figures where a source was found, see Module 1.3 notes in claude.md) until a
-- real automated source is chosen.
--
-- Only sales and PAT are required: they're the two line items every listed company reports every
-- quarter regardless of sector. Everything else is nullable because it isn't uniformly available
-- or even uniformly meaningful - banks/NBFCs don't report a "sales" line the way a manufacturer
-- does (net_interest_income is used as a stand-in, see the loader), ROCE is not a standard
-- disclosure for banks, and EPS/cash-flow-from-operations aren't always broken out at the
-- individual-quarter level in every summary source.
CREATE TABLE financial.financial_results (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id               uuid NOT NULL,
    period_end                  date NOT NULL,
    period_type                 varchar(10) NOT NULL,
    sales                       numeric(14, 2) NOT NULL,
    pat                         numeric(14, 2) NOT NULL,
    eps                         numeric(10, 2),
    roe_percentage              numeric(5, 2),
    roce_percentage             numeric(5, 2),
    operating_margin_percentage numeric(5, 2),
    net_margin_percentage       numeric(5, 2),
    cash_flow_from_operations   numeric(14, 2),
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_financial_results_period_type CHECK (period_type IN ('QUARTERLY', 'ANNUAL')),
    CONSTRAINT ux_financial_results_instrument_period UNIQUE (instrument_id, period_end, period_type)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_financial_results_instrument_id ON financial.financial_results (instrument_id);
CREATE INDEX ix_financial_results_period_end ON financial.financial_results (period_end);

CREATE TRIGGER trg_financial_results_updated_at
    BEFORE UPDATE ON financial.financial_results
    FOR EACH ROW EXECUTE FUNCTION financial.set_updated_at();
