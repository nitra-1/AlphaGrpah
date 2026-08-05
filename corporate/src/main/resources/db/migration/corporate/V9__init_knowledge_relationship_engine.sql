-- Module 2.7: Knowledge Relationship Engine. Per user direction, this is explicitly NOT a graph
-- database (Neo4j etc.) - at AlphaGraph's scale (8 tracked instruments today, a few thousand at
-- most later), the actual workload is 2-4 hop traversals (which companies benefit from PLI
-- Electronics, which competitors also serve BEL), well within what indexed relationship tables +
-- recursive CTEs handle in plain PostgreSQL. The graph is a semantic model, not a storage engine -
-- avoiding a second persistence technology (separate backup/deploy/consistency story, a second
-- query language) for a workload this doesn't require.
--
-- Two tables carry the whole model: entity_master (every real-world thing the graph can name -
-- companies, customers, themes, government schemes, competitors, sectors) and relationship
-- (directed, typed edges between two entities). Nothing else in the corporate module writes to
-- these directly - per the user's explicit design, extractors (OrderExtractor, ManagementExtractor,
-- NewsExtractor) only ever emit canonical facts; a new corporate.relationships.RelationshipBuilder
-- is the only writer, consuming those same facts and resolving free text into entity_id via
-- corporate.relationships.EntityResolver before a single graph row is created. The graph never
-- reads free text directly.
CREATE SCHEMA IF NOT EXISTS knowledge;

-- Every real-world thing the graph can name. entity_type is deliberately broader than "the 8
-- tracked instruments" - a CUSTOMER (Indian Air Force), a COMPETITOR (Syrma SGS, not one of
-- AlphaGraph's tracked instruments), a THEME (Semiconductor), or a GOVERNMENT_SCHEME (PLI
-- Electronics) are all first-class entities with no corresponding row anywhere else in the
-- database. linked_instrument_id/linked_sector_id are the two bridging columns back to
-- reference.instruments/reference.sectors (by value, no FK, matching every other cross-schema
-- reference in this project per docs/003_Database_Architecture.md §2) - populated only for
-- entity_type = COMPANY / SECTOR respectively that happen to already exist in the reference data;
-- an untracked competitor like Kaynes has both columns NULL.
--
-- Resolution is role-agnostic by design: canonical_name is globally unique regardless of
-- entity_type, so the same real-world company can never end up as two different entity_master
-- rows just because it was first observed as a COMPETITOR in one document and later as a genuine
-- COMPANY (an order customer) in another - EntityResolver always searches across every entity_type
-- for a name match first, and only uses the caller-supplied entity_type when actually creating a
-- brand-new row.
CREATE TABLE knowledge.entity_master (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type             varchar(20) NOT NULL,
    canonical_name          text NOT NULL,
    aliases                 text[] NOT NULL DEFAULT '{}',
    status                  varchar(20) NOT NULL DEFAULT 'ACTIVE',
    linked_instrument_id    uuid,
    linked_sector_id        uuid,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_entity_master_entity_type CHECK (entity_type IN (
        'COMPANY', 'CUSTOMER', 'THEME', 'GOVERNMENT_SCHEME', 'COMPETITOR', 'SECTOR'
    )),
    CONSTRAINT ck_entity_master_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ux_entity_master_canonical_name UNIQUE (canonical_name)
);

CREATE INDEX ix_entity_master_entity_type ON knowledge.entity_master (entity_type);
CREATE INDEX ix_entity_master_linked_instrument_id ON knowledge.entity_master (linked_instrument_id);

CREATE FUNCTION knowledge.set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_entity_master_updated_at
    BEFORE UPDATE ON knowledge.entity_master
    FOR EACH ROW EXECUTE FUNCTION knowledge.set_updated_at();

-- A directed, typed edge. Controlled vocabulary (relationship_type), no free text - per the
-- user's explicit design. source_document_id is nullable: most edges trace back to the document
-- that produced them (a real audit trail an LLM-extracted fact needs), but seeded edges (e.g.
-- BELONGS_TO_SECTOR, backfilled below from reference data; COMPETES_WITH, expanded from
-- knowledge.competitor_group) have no single source document. valid_from/valid_to exist for a
-- real future need (a CUSTOMER_OF relationship can genuinely end) but nothing in this module
-- closes one yet - every edge created here is open-ended (valid_to NULL), same
-- defined-but-not-yet-exercised pattern as corporate.corporate_actions.retry_count (Module 1.4).
--
-- Two unique constraints handle idempotent re-processing differently depending on provenance:
-- document-sourced edges dedup on (from, type, to, source_document_id) - reprocessing the same
-- document never creates a duplicate edge; seeded edges (source_document_id NULL) dedup via the
-- partial index below, since NULL is never equal to NULL in a plain UNIQUE constraint.
CREATE TABLE knowledge.relationship (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    from_entity_id      uuid NOT NULL REFERENCES knowledge.entity_master (id) ON DELETE CASCADE,
    relationship_type   varchar(30) NOT NULL,
    to_entity_id         uuid NOT NULL REFERENCES knowledge.entity_master (id) ON DELETE CASCADE,
    source_document_id  uuid REFERENCES corporate.documents (id) ON DELETE SET NULL,
    valid_from          timestamptz NOT NULL DEFAULT now(),
    valid_to            timestamptz,
    confidence          numeric(5,2) NOT NULL,
    created_by_engine   varchar(50) NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_relationship_type CHECK (relationship_type IN (
        'CUSTOMER_OF', 'SUPPLIER_OF', 'COMPETES_WITH', 'SUBSIDIARY_OF', 'PART_OF_THEME',
        'BENEFICIARY_OF', 'AFFECTED_BY', 'EXPORTS_TO', 'USES_COMMODITY', 'PARTNER_OF',
        'EXECUTES_FOR', 'OPERATES_IN', 'BELONGS_TO_SECTOR'
    )),
    CONSTRAINT ck_relationship_confidence CHECK (confidence >= 0 AND confidence <= 100),
    CONSTRAINT ux_relationship_with_document UNIQUE (from_entity_id, relationship_type, to_entity_id, source_document_id)
);

CREATE UNIQUE INDEX ux_relationship_no_document ON knowledge.relationship (from_entity_id, relationship_type, to_entity_id)
    WHERE source_document_id IS NULL;

CREATE INDEX ix_relationship_from_entity_id ON knowledge.relationship (from_entity_id);
CREATE INDEX ix_relationship_to_entity_id ON knowledge.relationship (to_entity_id);
CREATE INDEX ix_relationship_type ON knowledge.relationship (relationship_type);

-- A named group whose members are mutually COMPETES_WITH each other - per the user's explicit
-- design, competitors are never inferred dynamically from free text (too unreliable to trust for
-- a relationship type this consequential); a group is curated once and expanded into pairwise
-- COMPETES_WITH edges by corporate.relationships.RelationshipBuilder.
CREATE TABLE knowledge.competitor_group (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_competitor_group_name UNIQUE (name)
);

CREATE TABLE knowledge.competitor_group_member (
    competitor_group_id  uuid NOT NULL REFERENCES knowledge.competitor_group (id) ON DELETE CASCADE,
    entity_id            uuid NOT NULL REFERENCES knowledge.entity_master (id) ON DELETE CASCADE,
    PRIMARY KEY (competitor_group_id, entity_id)
);

-- One-time backfill: every already-tracked instrument and sector becomes a graph entity, so the
-- retrofitted Order Book/Management/News extractors have something to resolve against and
-- attach edges to from day one, rather than the graph starting completely empty. canonical_name
-- is the trading symbol (what every extractor and every existing table already uses as the
-- primary human-readable identifier); the instrument's full legal name becomes its first alias,
-- since documents commonly use either form ("BEL" vs "Bharat Electronics Limited").
INSERT INTO knowledge.entity_master (entity_type, canonical_name, aliases, linked_sector_id)
SELECT 'SECTOR', name, '{}', id
FROM reference.sectors;

INSERT INTO knowledge.entity_master (entity_type, canonical_name, aliases, linked_instrument_id)
SELECT 'COMPANY', symbol, ARRAY[name], id
FROM reference.instruments;

INSERT INTO knowledge.relationship (from_entity_id, relationship_type, to_entity_id, confidence, created_by_engine)
SELECT company.id, 'BELONGS_TO_SECTOR', sector.id, 100, 'SEED'
FROM reference.instruments i
JOIN knowledge.entity_master company ON company.linked_instrument_id = i.id
JOIN knowledge.entity_master sector ON sector.linked_sector_id = i.sector_id
WHERE i.sector_id IS NOT NULL;

-- Module 2.4 retrofit: order_book_ledger.customer was free text ("Ministry of Defence"); becomes
-- a resolved entity_id. This is what fixes a real, previously-disclosed limitation -
-- OrderBookSignalDetector's REPEAT_CUSTOMER signal used to do case-insensitive/trimmed string
-- matching on that free text (a customer named slightly differently across two documents would
-- never be recognized as the same one); comparing customer_entity_id instead is exact by
-- construction, since EntityResolver is the only thing that ever produces one.
ALTER TABLE corporate.order_book_ledger ADD COLUMN customer_entity_id uuid REFERENCES knowledge.entity_master (id);
ALTER TABLE corporate.order_book_ledger DROP COLUMN customer;

CREATE INDEX ix_order_book_ledger_customer_entity_id ON corporate.order_book_ledger (customer_entity_id);
