CREATE SCHEMA IF NOT EXISTS risk;

CREATE FUNCTION risk.set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- Module 1.9: the Risk Engine's output. Not a loss predictor - answers "what could invalidate
-- this investment?" by aggregating every other engine's already-computed output
-- (Technical/Fundamental/Ownership, Modules 1.5-1.7) plus a real-data-only Valuation read
-- (P/E, P/B derived from market's price and financial's EPS/PAT/equity - no new external source
-- needed). Event Risk (litigation, governance, pledged shares, auditor resignation) is
-- deliberately excluded per user direction - no free structured source has been confirmed for it,
-- unlike every other category here.
--
-- Unlike every prior engine's 0-100 scale (higher = stronger/better), risk_score keeps that same
-- direction for consistency across the whole system: higher = SAFER (lower actual risk), not
-- higher = riskier. A 100 means "no risk factors detected, several safety signals present"; a 0
-- means the opposite. See RiskEngine's javadoc for the bidirectional signal design that makes
-- this possible (every other engine's score can only ever be pulled down by risk factors; this
-- one can be pulled up by safety factors too).
CREATE TABLE risk.risk_scores (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id      uuid NOT NULL,
    as_of_date         date NOT NULL,
    business_risk      varchar(20) NOT NULL,
    technical_risk     varchar(20) NOT NULL,
    ownership_risk     varchar(20) NOT NULL,
    valuation_risk     varchar(20) NOT NULL,
    overall_risk       varchar(20) NOT NULL,
    risk_score         numeric(5, 2) NOT NULL,
    confidence         numeric(5, 2) NOT NULL,
    pe_ratio           numeric(8, 2),
    pb_ratio           numeric(8, 2),
    debt_to_equity     numeric(6, 2),
    rule_set_version   integer NOT NULL,
    computed_at        timestamptz NOT NULL DEFAULT now(),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_risk_scores_business_risk CHECK (business_risk IN ('VERY_LOW', 'LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH')),
    CONSTRAINT ck_risk_scores_technical_risk CHECK (technical_risk IN ('VERY_LOW', 'LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH')),
    CONSTRAINT ck_risk_scores_ownership_risk CHECK (ownership_risk IN ('VERY_LOW', 'LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH')),
    CONSTRAINT ck_risk_scores_valuation_risk CHECK (valuation_risk IN ('VERY_LOW', 'LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH')),
    CONSTRAINT ck_risk_scores_overall_risk CHECK (overall_risk IN ('VERY_LOW', 'LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH')),
    CONSTRAINT ux_risk_scores_instrument_date UNIQUE (instrument_id, as_of_date)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_risk_scores_instrument_id ON risk.risk_scores (instrument_id);
CREATE INDEX ix_risk_scores_as_of_date ON risk.risk_scores (as_of_date);

CREATE TRIGGER trg_risk_scores_updated_at
    BEFORE UPDATE ON risk.risk_scores
    FOR EACH ROW EXECUTE FUNCTION risk.set_updated_at();
