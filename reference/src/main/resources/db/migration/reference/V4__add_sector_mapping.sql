-- Module 1.8 (Sector Engine): "Sector Mapping" is explicitly listed as an engine input the
-- roadmap expects to exist, but reference.instruments never had a sector_id column at all, and
-- V2's sector tree ("Industrials > Capital Goods > Construction & Engineering") was explicit
-- dummy/placeholder data per its own migration comment, never actually linked to any instrument.
--
-- Real sector classifications for the 8 seeded instruments, matching standard NSE sector
-- groupings (well-established, no external research needed unlike the murkier financial/
-- ownership data points in Modules 1.6/1.7):
--   Information Technology: TCS, INFY
--   Financial Services:     HDFCBANK, ICICIBANK
--   Capital Goods:          ESCORTS, BEML, ACE
--   Energy:                 RELIANCE
-- This gives 3 sectors with multiple constituents (real breadth/participation math) and one
-- single-constituent sector (RELIANCE alone in Energy) - a real, disclosed consequence of only
-- tracking 8 instruments overall, not something this migration works around.
--
-- reference.sectors has no unique constraint on name, so "Capital Goods" needs care: V2's dummy
-- tree (Module 0.4) already created a row named "Capital Goods" as a child of "Industrials" -
-- inserting a second, unrelated "Capital Goods" row here would make
-- "WHERE name = 'Capital Goods'" ambiguous (confirmed the hard way: Postgres error "more than one
-- row returned by a subquery used as an expression"). Reusing that existing row instead of
-- duplicating it - it already represents the same real category, it was just never linked to any
-- instrument.
ALTER TABLE reference.instruments ADD COLUMN sector_id uuid REFERENCES reference.sectors (id);

INSERT INTO reference.sectors (name, parent_sector_id)
VALUES
    ('Information Technology', NULL),
    ('Financial Services', NULL),
    ('Energy', NULL)
ON CONFLICT DO NOTHING;

UPDATE reference.instruments SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'Information Technology')
WHERE symbol IN ('TCS', 'INFY');

UPDATE reference.instruments SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'Financial Services')
WHERE symbol IN ('HDFCBANK', 'ICICIBANK');

UPDATE reference.instruments
SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'Capital Goods' AND parent_sector_id IS NOT NULL)
WHERE symbol IN ('ESCORTS', 'BEML', 'ACE');

UPDATE reference.instruments SET sector_id = (SELECT id FROM reference.sectors WHERE name = 'Energy')
WHERE symbol = 'RELIANCE';
