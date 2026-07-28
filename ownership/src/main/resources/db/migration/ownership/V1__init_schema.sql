CREATE SCHEMA IF NOT EXISTS ownership;

CREATE FUNCTION ownership.set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- Unlike market.daily_prices (Module 1.1), there's no free public bulk file for shareholding
-- pattern data - it's filed per-company via NEAPS, with bulk access behind NSE's paid Corporate
-- Data Subscription. This module ships against a manually-compiled sample (real percentages
-- looked up per company, see Module 1.2 notes in claude.md) until a real automated source is
-- chosen.
CREATE TABLE ownership.shareholding_pattern (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id         uuid NOT NULL,
    period_end            date NOT NULL,
    promoter_percentage   numeric(5, 2) NOT NULL,
    fii_percentage        numeric(5, 2) NOT NULL,
    dii_percentage        numeric(5, 2) NOT NULL,
    mf_percentage         numeric(5, 2),
    public_percentage     numeric(5, 2),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_shareholding_pattern_instrument_period UNIQUE (instrument_id, period_end)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_shareholding_pattern_instrument_id ON ownership.shareholding_pattern (instrument_id);
CREATE INDEX ix_shareholding_pattern_period_end ON ownership.shareholding_pattern (period_end);

CREATE TRIGGER trg_shareholding_pattern_updated_at
    BEFORE UPDATE ON ownership.shareholding_pattern
    FOR EACH ROW EXECUTE FUNCTION ownership.set_updated_at();
