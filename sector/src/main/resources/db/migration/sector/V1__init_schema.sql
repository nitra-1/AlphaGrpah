CREATE SCHEMA IF NOT EXISTS sector;

CREATE FUNCTION sector.set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- Module 1.8: the Sector Engine's output. Answers "where is money moving?" by comparing
-- SECTORS, not individual stocks - the first engine that aggregates across many instruments into
-- one score rather than scoring one instrument at a time (Technical/Fundamental/Institutional,
-- Modules 1.5-1.7, were all one-instrument-in, one-score-out).
--
-- sector_name is denormalized (real sector_id also stored, no cross-schema FK per
-- docs/003_Database_Architecture.md §2) so a row is self-describing without a join back to
-- reference.sectors. constituent_count is stored because it directly affects how meaningful
-- breadth/participation are - a 1-constituent sector's breadth is trivially 0% or 100%, not a
-- real cross-sectional signal, and this column makes that visible rather than hidden.
CREATE TABLE sector.sector_scores (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sector_id                 uuid NOT NULL,
    sector_name               text NOT NULL,
    as_of_date                date NOT NULL,
    leadership                varchar(20) NOT NULL,
    momentum                  varchar(20) NOT NULL,
    rotation                  varchar(20) NOT NULL,
    money_flow                varchar(20) NOT NULL,
    sector_score              numeric(5, 2) NOT NULL,
    confidence                numeric(5, 2) NOT NULL,
    relative_strength         numeric(7, 2),
    sector_performance_pct    numeric(7, 2),
    sector_volume_ratio       numeric(6, 2),
    breadth_pct               numeric(5, 2),
    participation_pct         numeric(5, 2),
    constituent_count         integer NOT NULL,
    rule_set_version          integer NOT NULL,
    computed_at               timestamptz NOT NULL DEFAULT now(),
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_sector_scores_leadership CHECK (leadership IN ('INCREASING', 'STABLE', 'DECREASING')),
    CONSTRAINT ck_sector_scores_momentum CHECK (momentum IN ('VERY_STRONG', 'STRONG', 'MODERATE', 'WEAK', 'VERY_WEAK')),
    CONSTRAINT ck_sector_scores_rotation CHECK (rotation IN ('POSITIVE', 'NEUTRAL', 'NEGATIVE')),
    CONSTRAINT ck_sector_scores_money_flow CHECK (money_flow IN ('STRONG', 'MODERATE', 'WEAK')),
    CONSTRAINT ux_sector_scores_sector_date UNIQUE (sector_id, as_of_date)
);

-- sector_id references reference.sectors by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_sector_scores_sector_id ON sector.sector_scores (sector_id);
CREATE INDEX ix_sector_scores_as_of_date ON sector.sector_scores (as_of_date);

CREATE TRIGGER trg_sector_scores_updated_at
    BEFORE UPDATE ON sector.sector_scores
    FOR EACH ROW EXECUTE FUNCTION sector.set_updated_at();
