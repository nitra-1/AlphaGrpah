CREATE SCHEMA IF NOT EXISTS technical;

CREATE FUNCTION technical.set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- One row per instrument per date: the Technical Engine's output (docs/002_Engine_Architecture.md
-- §5) plus the raw indicator values it was computed from, kept alongside the classification for
-- traceability/debugging rather than in a separate table. sma200 is nullable and will stay null
-- until ~200 real trading days of market.daily_prices history exist for an instrument (Module 1.5
-- backfilled ~95 real days - enough for every other indicator here, not that one). stage is
-- nullable for the same reason: a confident Stage classification needs the weekly SMA30 to have
-- enough history to show a trend, not just a single reading.
CREATE TABLE technical.technical_scores (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id           uuid NOT NULL,
    as_of_date              date NOT NULL,
    trend                   varchar(20) NOT NULL,
    trend_confidence        numeric(5, 2) NOT NULL,
    momentum                varchar(20) NOT NULL,
    volume_state            varchar(20) NOT NULL,
    breakout_status         varchar(20) NOT NULL,
    stage                   smallint,
    market_behaviour_score  numeric(5, 2) NOT NULL,
    sma20                   numeric,
    sma50                   numeric,
    sma200                  numeric,
    weekly_sma30            numeric,
    rsi14                   numeric(5, 2),
    macd_line               numeric,
    macd_signal             numeric,
    macd_histogram          numeric,
    adx14                   numeric(5, 2),
    atr14                   numeric,
    obv                     bigint,
    relative_volume         numeric(6, 2),
    rule_set_version        integer NOT NULL,
    computed_at             timestamptz NOT NULL DEFAULT now(),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_technical_scores_trend
        CHECK (trend IN ('STRONG_UPTREND', 'UPTREND', 'SIDEWAYS', 'DOWNTREND', 'STRONG_DOWNTREND')),
    CONSTRAINT ck_technical_scores_momentum CHECK (momentum IN ('IMPROVING', 'WEAKENING', 'NEUTRAL')),
    CONSTRAINT ck_technical_scores_volume_state CHECK (volume_state IN ('STRONG', 'AVERAGE', 'WEAK')),
    CONSTRAINT ck_technical_scores_breakout_status CHECK (breakout_status IN ('CONFIRMED', 'PENDING', 'NONE')),
    CONSTRAINT ck_technical_scores_stage CHECK (stage IS NULL OR stage BETWEEN 1 AND 4),
    CONSTRAINT ux_technical_scores_instrument_date UNIQUE (instrument_id, as_of_date)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_technical_scores_instrument_id ON technical.technical_scores (instrument_id);
CREATE INDEX ix_technical_scores_as_of_date ON technical.technical_scores (as_of_date);

CREATE TRIGGER trg_technical_scores_updated_at
    BEFORE UPDATE ON technical.technical_scores
    FOR EACH ROW EXECUTE FUNCTION technical.set_updated_at();
