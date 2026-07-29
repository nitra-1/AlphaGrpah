-- Module 1.6: the Fundamental Engine's output. Answers "is the business becoming stronger?"
-- from financial.financial_results alone - no price, no charts (mirrors the Technical Engine's
-- price-only mandate from Module 1.5, inverted).
--
-- Growth fields (revenue/pat/eps_growth_percentage) are nullable: they need a same-period-type
-- prior year to compare against, which only exists for the 6 companies Module 1.6 researched a
-- second real quarter for (see financial-data/sample-financial-results.csv) - BEML/ACE/ZOMATO
-- have none.
--
-- Efficiency fields (asset_turnover, working_capital, cash_conversion_ratio) and Leverage fields
-- (debt_to_equity, interest_coverage) are nullable for the same reason as sma200 in Module 1.5:
-- genuinely missing inputs (balance sheet data, cash-flow-from-operations) rather than a
-- computation that failed. Not populated at all for HDFCBANK/ICICIBANK - see
-- FinancialResultsScheduledPipeline's javadoc for why a bank's balance sheet doesn't map onto
-- these ratios without a sector-aware model.
CREATE TABLE financial.fundamental_scores (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id               uuid NOT NULL,
    as_of_date                  date NOT NULL,
    business_growth             varchar(20) NOT NULL,
    profitability               varchar(20) NOT NULL,
    capital_efficiency          varchar(20) NOT NULL,
    financial_quality           varchar(20) NOT NULL,
    financial_score             numeric(5, 2) NOT NULL,
    confidence                  numeric(5, 2) NOT NULL,
    revenue_growth_percentage   numeric(7, 2),
    pat_growth_percentage       numeric(7, 2),
    eps_growth_percentage       numeric(7, 2),
    roe_percentage              numeric(5, 2),
    roce_percentage             numeric(5, 2),
    operating_margin_percentage numeric(5, 2),
    net_margin_percentage       numeric(5, 2),
    asset_turnover              numeric(6, 2),
    working_capital             numeric(16, 2),
    cash_conversion_ratio       numeric(6, 2),
    debt_to_equity              numeric(6, 2),
    interest_coverage           numeric(8, 2),
    rule_set_version            integer NOT NULL,
    computed_at                 timestamptz NOT NULL DEFAULT now(),
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_fundamental_scores_business_growth
        CHECK (business_growth IN ('EXCELLENT', 'GOOD', 'MODERATE', 'WEAK', 'DECLINING')),
    CONSTRAINT ck_fundamental_scores_profitability CHECK (profitability IN ('IMPROVING', 'STABLE', 'DECLINING')),
    CONSTRAINT ck_fundamental_scores_capital_efficiency CHECK (capital_efficiency IN ('HIGH', 'MODERATE', 'LOW')),
    CONSTRAINT ck_fundamental_scores_financial_quality CHECK (financial_quality IN ('STRONG', 'ADEQUATE', 'WEAK')),
    CONSTRAINT ux_fundamental_scores_instrument_date UNIQUE (instrument_id, as_of_date)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_fundamental_scores_instrument_id ON financial.fundamental_scores (instrument_id);
CREATE INDEX ix_fundamental_scores_as_of_date ON financial.fundamental_scores (as_of_date);

CREATE TRIGGER trg_fundamental_scores_updated_at
    BEFORE UPDATE ON financial.fundamental_scores
    FOR EACH ROW EXECUTE FUNCTION financial.set_updated_at();
