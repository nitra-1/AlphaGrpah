-- Outcome Evidence Enrichment: maps a sector to a real, verified tradable NSE sectoral index
-- instrument (in reference.instruments) for sector-relative forward returns. Deliberately NOT
-- auto-populated for every sector - many of this platform's sector groupings (e.g. "Capital
-- Goods", "Construction & Engineering") don't have a standard tradable NSE sectoral index, and
-- guessing one would be false precision. A sector with no row here means every forward_outcome
-- for its instruments correctly gets NULL sector-relative fields, not a fabricated fallback.
--
-- Empty on purpose in this migration: seeding a mapping here would only be honest once a real
-- price-ingestion pipeline exists for that specific sectoral index (NIFTY IT, NIFTY PHARMA, etc.),
-- and only the broad-market NIFTY 50 collector was built in this slice - not one per sector index.
-- The table and reader are ready for whichever sector gets a real index pipeline next.
CREATE TABLE reference.sector_benchmarks (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sector_id               uuid NOT NULL,
    benchmark_instrument_id uuid NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_sector_benchmarks_sector_id UNIQUE (sector_id)
);
