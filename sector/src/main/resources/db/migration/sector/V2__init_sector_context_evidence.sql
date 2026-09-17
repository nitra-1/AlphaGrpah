-- Sector Context evidence (Multibagger Discovery Stage 1, Tier 5 of the remaining 7 evidence
-- families - see claude.md). Completes the "full triangle" the plan asked for from one
-- instrument-level engine: stock vs. Nifty, stock vs. its own sector, sector vs. Nifty.
--
-- INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY/_VS_SECTOR are computed spreads (instrument's own rolling
-- 20-day return minus a benchmark's, reusing market.transformation_evidence's PRICE_RETURN_20D
-- rather than recomputing it - see intelligence.sectorcontext). SECTOR_RELATIVE_STRENGTH is a
-- straight re-persist of the already-real, already-daily-computed sector.sector_scores.relative_strength,
-- attributed to each of that sector's member instruments - multiple instruments in the same sector
-- share the identical value on the same day by design, not a bug.
--
-- Written by sector.transformation.SectorContextEvidenceWriter even though the computation itself
-- lives in intelligence.sectorcontext (needs both market and sector data, so it's built as an
-- intelligence bridge, same convention intelligence.sector.SectorAnalysisOrchestrator already
-- uses) - matches this codebase's existing rule that a domain's own schema is always written by a
-- class that domain module owns, never directly from intelligence.
CREATE TABLE sector.transformation_evidence (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id     uuid NOT NULL,
    symbol            text NOT NULL,
    metric_name       varchar(40) NOT NULL,
    as_of_date        date NOT NULL,
    prior_as_of_date  date NULL,
    value             numeric(9, 4) NOT NULL,
    prior_value       numeric(9, 4) NULL,
    change            numeric(9, 4) NULL,
    persistence_days  integer NOT NULL DEFAULT 0,
    confidence        numeric(5, 2) NOT NULL,
    source            varchar(30) NOT NULL DEFAULT 'SECTOR_CONTEXT',
    rule_version      integer NOT NULL DEFAULT 1,
    computed_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_sector_transformation_evidence_metric CHECK (metric_name IN (
        'INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY', 'INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR', 'SECTOR_RELATIVE_STRENGTH'
    )),
    CONSTRAINT ux_sector_transformation_evidence_instrument_metric_date UNIQUE (instrument_id, metric_name, as_of_date)
);

CREATE INDEX ix_sector_transformation_evidence_instrument_id ON sector.transformation_evidence (instrument_id);
