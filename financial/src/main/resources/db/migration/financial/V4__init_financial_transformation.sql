-- Business + Earnings Inflection evidence, plus Balance-Sheet's INTEREST_EXPENSE piggybacked on
-- the same live collector (Multibagger Discovery Stage 1, Tier 2+3 of the remaining 7 evidence
-- families - see claude.md). Confirmed live: NSE's results-comparision endpoint returns exactly 5
-- real quarters per symbol, index 0 (most recent) through index 4 - for a contiguous quarterly
-- filer, index 4 is the same calendar quarter one year earlier, so REVENUE/PAT/OPERATING_MARGIN/
-- INTEREST_EXPENSE evidence prefers a real year-over-year comparison over a sequential
-- quarter-over-quarter one when that fifth point genuinely lands ~12 months back - comparator_used
-- discloses which was actually used, never silently picking one.
--
-- REVENUE/OPERATING_MARGIN/INTEREST_EXPENSE all come from fields the real feed only populates for
-- non-bank filers (bankNonBnking != 'B') - a bank's income statement doesn't share the same
-- structure (re_net_sale/re_int_new/re_oth_inc_new are simply absent on a bank's real response),
-- matching this project's own existing disclosed limitation that a bank's financials don't map
-- onto the same model (financial.results.FinancialResultsScheduledPipeline's javadoc). PAT
-- (re_net_profit) is the one metric genuinely reported the same way for both, so it's the only one
-- of the four with real bank coverage in this table.
--
-- numeric(18,4) on the value columns: real figures come back in Rs Lakh (RELIANCE's real Q3 FY25
-- revenue is 12,826,000 = Rs 1,28,260 crore, per the feed's own segment note) - generous headroom
-- given the numeric overflow already hit once in the ownership XBRL pass from an under-sized column.
CREATE TABLE financial.transformation_evidence (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id          uuid NOT NULL,
    symbol                 text NOT NULL,
    metric_name            varchar(40) NOT NULL,
    period_end             date NOT NULL,
    prior_period_end       date NULL,
    value                  numeric(18, 4) NOT NULL,
    prior_value            numeric(18, 4) NULL,
    change                 numeric(18, 4) NULL,
    velocity_per_quarter   numeric(18, 4) NULL,
    persistence_quarters   integer NOT NULL DEFAULT 0,
    confidence             numeric(5, 2) NOT NULL,
    comparator_used        varchar(10) NULL,
    source                 varchar(30) NOT NULL DEFAULT 'NSE_RESULTS_COMPARISION',
    rule_version           integer NOT NULL DEFAULT 1,
    computed_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_financial_transformation_evidence_metric CHECK (metric_name IN (
        'REVENUE', 'PAT', 'OPERATING_MARGIN', 'INTEREST_EXPENSE'
    )),
    CONSTRAINT ck_financial_transformation_evidence_comparator CHECK (comparator_used IN ('YOY', 'QOQ_ONLY')),
    CONSTRAINT ux_financial_transformation_evidence_instrument_metric_period UNIQUE (instrument_id, metric_name, period_end)
);

CREATE INDEX ix_financial_transformation_evidence_instrument_id ON financial.transformation_evidence (instrument_id);
