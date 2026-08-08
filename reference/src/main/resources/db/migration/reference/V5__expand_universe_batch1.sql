-- Universe expansion, batch 1: 8 tracked instruments (all seeded via V3) is too narrow for
-- "advise me on opportunities" to mean anything - every scoring/ranking engine can only ever
-- surface what's already in reference.instruments. This adds 12 more real NSE large-caps,
-- deliberately chosen to fill sectors the original 8 don't touch at all (FMCG, Pharma, Auto,
-- Metals, Cement, Telecom, Power, Consumer Durables were previously zero-coverage; the existing
-- but empty "Construction & Engineering" sector row from V4's dummy tree gets its first real
-- constituent here too). ISINs verified against live NSE/broker listings, not recalled from
-- training data - the same discipline V3's fix of BEML's fabricated ISIN established.
--
-- TATAMOTORS was deliberately excluded from this batch: it underwent a real demerger in
-- Oct-Nov 2025 (passenger vehicles now trade as TMPV under the old ISIN INE155A01022; commercial
-- vehicles spun off as a newly renamed "Tata Motors Ltd" trading as TMCV). Backfilling clean
-- price history through a corporate-action discontinuity isn't worth the complexity for a first
-- batch - Mahindra & Mahindra (M&M) fills the Automobile sector slot instead, no demerger involved.
INSERT INTO reference.instruments (symbol, exchange_id, name, isin, instrument_type)
SELECT v.symbol, e.id, v.name, v.isin, 'EQUITY'
FROM reference.exchanges e
CROSS JOIN (VALUES
    ('HINDUNILVR', 'Hindustan Unilever Ltd', 'INE030A01027'),
    ('ITC',        'ITC Ltd',                'INE154A01025'),
    ('SUNPHARMA',  'Sun Pharmaceutical Industries Ltd', 'INE044A01036'),
    ('MARUTI',     'Maruti Suzuki India Ltd', 'INE585B01010'),
    ('M&M',        'Mahindra & Mahindra Ltd', 'INE101A01026'),
    ('TATASTEEL',  'Tata Steel Ltd',          'INE081A01020'),
    ('ULTRACEMCO', 'UltraTech Cement Ltd',    'INE481G01011'),
    ('BHARTIARTL', 'Bharti Airtel Ltd',       'INE397D01024'),
    ('NTPC',       'NTPC Ltd',                'INE733E01010'),
    ('ASIANPAINT', 'Asian Paints Ltd',        'INE021A01026'),
    ('LT',         'Larsen & Toubro Ltd',     'INE018A01030'),
    ('SBIN',       'State Bank of India',     'INE062A01020')
) AS v(symbol, name, isin)
WHERE e.code = 'NSE'
ON CONFLICT (symbol, exchange_id) DO NOTHING;

INSERT INTO reference.sectors (name, parent_sector_id)
VALUES
    ('FMCG', NULL),
    ('Pharmaceuticals', NULL),
    ('Automobile', NULL),
    ('Metals & Mining', NULL),
    ('Cement & Cement Products', NULL),
    ('Telecommunication', NULL),
    ('Power', NULL),
    ('Consumer Durables', NULL)
ON CONFLICT DO NOTHING;

UPDATE reference.instruments SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'FMCG')
WHERE symbol IN ('HINDUNILVR', 'ITC');

UPDATE reference.instruments SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'Pharmaceuticals')
WHERE symbol = 'SUNPHARMA';

UPDATE reference.instruments SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'Automobile')
WHERE symbol IN ('MARUTI', 'M&M');

UPDATE reference.instruments SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'Metals & Mining')
WHERE symbol = 'TATASTEEL';

UPDATE reference.instruments SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'Cement & Cement Products')
WHERE symbol = 'ULTRACEMCO';

UPDATE reference.instruments SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'Telecommunication')
WHERE symbol = 'BHARTIARTL';

UPDATE reference.instruments SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'Power')
WHERE symbol = 'NTPC';

UPDATE reference.instruments SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'Consumer Durables')
WHERE symbol = 'ASIANPAINT';

-- Reuses the existing "Construction & Engineering" row from V4's dummy tree (a child of
-- Industrials) rather than creating a duplicate - it was defined but never linked to any
-- instrument until now.
UPDATE reference.instruments
SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'Construction & Engineering' AND parent_sector_id IS NOT NULL)
WHERE symbol = 'LT';

UPDATE reference.instruments SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'Financial Services')
WHERE symbol = 'SBIN';
