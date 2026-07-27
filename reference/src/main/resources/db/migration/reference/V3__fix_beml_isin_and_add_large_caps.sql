-- BEML's ISIN in V2's seed data was fabricated and wrong (INE258A01016). Confirmed against a
-- real NSE security master download (Module 1.1) that the actual ISIN is INE258A01024.
UPDATE reference.instruments
SET isin = 'INE258A01024'
WHERE symbol = 'BEML' AND isin = 'INE258A01016';

-- A handful of additional real large-caps, ISINs confirmed against the same real security
-- master download, so Module 1.1's bundled market data sample has more than 3 instruments to
-- work with.
INSERT INTO reference.instruments (symbol, exchange_id, name, isin, instrument_type)
SELECT 'RELIANCE', e.id, 'Reliance Industries Ltd', 'INE002A01018', 'EQUITY'
FROM reference.exchanges e WHERE e.code = 'NSE'
ON CONFLICT (symbol, exchange_id) DO NOTHING;

INSERT INTO reference.instruments (symbol, exchange_id, name, isin, instrument_type)
SELECT 'TCS', e.id, 'Tata Consultancy Services Ltd', 'INE467B01029', 'EQUITY'
FROM reference.exchanges e WHERE e.code = 'NSE'
ON CONFLICT (symbol, exchange_id) DO NOTHING;

INSERT INTO reference.instruments (symbol, exchange_id, name, isin, instrument_type)
SELECT 'INFY', e.id, 'Infosys Ltd', 'INE009A01021', 'EQUITY'
FROM reference.exchanges e WHERE e.code = 'NSE'
ON CONFLICT (symbol, exchange_id) DO NOTHING;

INSERT INTO reference.instruments (symbol, exchange_id, name, isin, instrument_type)
SELECT 'HDFCBANK', e.id, 'HDFC Bank Ltd', 'INE040A01034', 'EQUITY'
FROM reference.exchanges e WHERE e.code = 'NSE'
ON CONFLICT (symbol, exchange_id) DO NOTHING;

INSERT INTO reference.instruments (symbol, exchange_id, name, isin, instrument_type)
SELECT 'ICICIBANK', e.id, 'ICICI Bank Ltd', 'INE090A01021', 'EQUITY'
FROM reference.exchanges e WHERE e.code = 'NSE'
ON CONFLICT (symbol, exchange_id) DO NOTHING;
