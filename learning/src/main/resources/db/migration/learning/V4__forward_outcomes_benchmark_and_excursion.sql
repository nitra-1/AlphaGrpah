-- Outcome Evidence Enrichment: forward_outcomes previously only answered "did the stock go up or
-- down" (forward_return_percentage). These columns add "did it beat the market/sector" and "what
-- risk/reward path did it take" (max favorable/adverse excursion). All nullable - market requires
-- a real tracked index instrument (e.g. NIFTY 50), sector requires a verified
-- reference.sector_benchmarks mapping that not every sector has; both stay NULL rather than
-- fabricated when the underlying data doesn't exist. The *_benchmark_outcome_date columns record
-- the exact trading date each benchmark return was measured on - alignment provenance, since a
-- holiday/listing gap on the benchmark's own calendar means it's simply not computed for that
-- horizon, never silently substituted from a nearby date.
ALTER TABLE learning.forward_outcomes
    ADD COLUMN market_benchmark_instrument_id uuid,
    ADD COLUMN market_benchmark_return_percentage numeric(7, 2),
    ADD COLUMN market_benchmark_outcome_date date,
    ADD COLUMN excess_return_market_percentage numeric(7, 2),

    ADD COLUMN sector_benchmark_instrument_id uuid,
    ADD COLUMN sector_benchmark_return_percentage numeric(7, 2),
    ADD COLUMN sector_benchmark_outcome_date date,
    ADD COLUMN excess_return_sector_percentage numeric(7, 2),

    ADD COLUMN mfe_percentage numeric(7, 2),
    ADD COLUMN mae_percentage numeric(7, 2);
