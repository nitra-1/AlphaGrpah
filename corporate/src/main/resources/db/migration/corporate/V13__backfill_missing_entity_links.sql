-- V9__init_knowledge_relationship_engine.sql's backfill only ever covered the instruments tracked
-- at the moment that migration ran (8 at the time). Every instrument added afterward - 51 of the
-- 59 currently tracked, including HCLTECH - got no knowledge.entity_master row at all, so
-- NewsInstrumentMatcher (which requires linked_instrument_id IS NOT NULL) could never match real
-- news naming them, and RelationshipBuilder would silently create a second, orphaned entity the
-- first time any extractor mentioned one. api.admin.InstrumentAdditionService now links every
-- future instrument at creation time (EntityResolver.linkTrackedInstrument) - this migration is
-- the one-time catch-up for every instrument that predates that fix.
--
-- Same convention as V9's original seed: canonical_name is the trading symbol, the instrument's
-- full legal name becomes its first alias. ON CONFLICT (canonical_name) DO UPDATE handles the case
-- where an extractor already created an unlinked entity under this exact symbol before this
-- migration ran - the row gets linked, never duplicated.
INSERT INTO knowledge.entity_master (entity_type, canonical_name, aliases, linked_instrument_id)
SELECT 'COMPANY', i.symbol, ARRAY[i.name], i.id
FROM reference.instruments i
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge.entity_master em WHERE em.linked_instrument_id = i.id
)
ON CONFLICT (canonical_name) DO UPDATE SET linked_instrument_id = EXCLUDED.linked_instrument_id;
