-- Real bug found live: the original XBRL-enrichment candidate query (V13) treated "any of the
-- four sub-category columns still NULL" as "still needs enrichment." That's wrong for a real,
-- valid case - a company's real filing can genuinely have zero holders in one of these categories
-- (e.g. no foreign portfolio investors at all), so that column would legitimately stay null
-- forever, and the old query would re-select and re-fetch that same period's real XBRL document on
-- every single future run, indefinitely, for no benefit.
--
-- xbrl_enriched_at separates "have we successfully attempted this period" from "what did we
-- extract" - set once by XbrlShareholdingWriter after a real fetch+parse completes, regardless of
-- how many of the four categories the document actually contained. The candidate reader now
-- selects on this column being null, not on the percentage columns.
ALTER TABLE ownership.shareholding_pattern
    ADD COLUMN xbrl_enriched_at timestamptz NULL;
