-- Dummy seed data per docs/003_Database_Architecture.md §5 (Module 0.4: exchanges, a handful
-- of dummy instruments, one sector tree). Safe to re-run across environments.

INSERT INTO reference.exchanges (code, name, country)
VALUES
    ('NSE', 'National Stock Exchange of India', 'IN'),
    ('BSE', 'Bombay Stock Exchange', 'IN')
ON CONFLICT (code) DO NOTHING;

INSERT INTO reference.sectors (name, parent_sector_id)
VALUES ('Industrials', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO reference.sectors (name, parent_sector_id)
SELECT 'Capital Goods', id FROM reference.sectors WHERE name = 'Industrials'
ON CONFLICT DO NOTHING;

INSERT INTO reference.sectors (name, parent_sector_id)
SELECT 'Construction & Engineering', id FROM reference.sectors WHERE name = 'Capital Goods'
ON CONFLICT DO NOTHING;

INSERT INTO reference.instruments (symbol, exchange_id, name, isin, instrument_type)
SELECT 'BEML', e.id, 'BEML Limited', 'INE258A01016', 'EQUITY'
FROM reference.exchanges e WHERE e.code = 'NSE'
ON CONFLICT (symbol, exchange_id) DO NOTHING;

INSERT INTO reference.instruments (symbol, exchange_id, name, isin, instrument_type)
SELECT 'ACE', e.id, 'Action Construction Equipment Limited', 'INE731H01025', 'EQUITY'
FROM reference.exchanges e WHERE e.code = 'NSE'
ON CONFLICT (symbol, exchange_id) DO NOTHING;

INSERT INTO reference.instruments (symbol, exchange_id, name, isin, instrument_type)
SELECT 'ESCORTS', e.id, 'Escorts Kubota Limited', 'INE042A01014', 'EQUITY'
FROM reference.exchanges e WHERE e.code = 'NSE'
ON CONFLICT (symbol, exchange_id) DO NOTHING;
