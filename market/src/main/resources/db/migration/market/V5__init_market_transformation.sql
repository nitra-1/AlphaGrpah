-- Market Accumulation evidence (Multibagger Discovery Stage 1 - Tier 1 of the remaining 7
-- evidence families, see claude.md). Same append-only evidence-ledger shape as
-- ownership.transformation_evidence (V13), but daily-cadence, not quarterly:
-- market.daily_prices already has one row per trading day, so change/velocity here compare
-- today's value to the most recent PRIOR TRADING DAY's value, never a quarter-over-quarter
-- comparison. velocity_per_day is therefore always numerically equal to change (the gap between
-- two adjacent daily_prices rows is always exactly one trading session) - kept as its own column
-- anyway for shape-consistency with every other evidence family, not because it carries
-- independent information here.
--
-- numeric(14,4) on the value columns is deliberately generous: RELATIVE_VOLUME is a ratio that can
-- spike far past 1.0 on a real volume event, and PRICE_RETURN_20D can be a large percentage for a
-- volatile small-cap - the ownership XBRL enrichment pass already hit a real numeric field overflow
-- once from an under-sized column (see V14's comment there), not repeating that here.
CREATE TABLE market.transformation_evidence (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id     uuid NOT NULL,
    symbol            text NOT NULL,
    metric_name       varchar(40) NOT NULL,
    trade_date        date NOT NULL,
    prior_trade_date  date NULL,
    value             numeric(14, 4) NOT NULL,
    prior_value       numeric(14, 4) NULL,
    change            numeric(14, 4) NULL,
    velocity_per_day  numeric(14, 4) NULL,
    persistence_days  integer NOT NULL DEFAULT 0,
    confidence        numeric(5, 2) NOT NULL,
    source            varchar(30) NOT NULL DEFAULT 'MARKET_DAILY_PRICES',
    rule_version      integer NOT NULL DEFAULT 1,
    computed_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_market_transformation_evidence_metric CHECK (metric_name IN (
        'RELATIVE_VOLUME', 'DELIVERY_PERCENTAGE_20D_AVG', 'PRICE_RETURN_20D'
    )),
    CONSTRAINT ux_market_transformation_evidence_instrument_metric_date UNIQUE (instrument_id, metric_name, trade_date)
);

CREATE INDEX ix_market_transformation_evidence_instrument_id ON market.transformation_evidence (instrument_id);
