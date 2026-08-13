-- Outcome Evidence Enrichment: NIFTY 50 as a real tracked instrument, so market.daily_prices (via
-- market.pricing.NiftyIndexScheduledPipeline) and learning.outcomes.BenchmarkReturnCalculator
-- (default alphagraph.learning.market-benchmark-symbol=NIFTY50) can both resolve it the same way
-- any equity is resolved - reusing the existing instrument+daily_prices infrastructure rather than
-- inventing an index-specific table. instrument_type is 'INDEX', not 'EQUITY' - it isn't a company.
-- isin is intentionally NULL: no real NSE index ISIN was verified, so none is guessed here.
INSERT INTO reference.instruments (symbol, exchange_id, name, isin, instrument_type)
SELECT 'NIFTY50', e.id, 'NIFTY 50', NULL, 'INDEX'
FROM reference.exchanges e WHERE e.code = 'NSE'
ON CONFLICT (symbol, exchange_id) DO NOTHING;
