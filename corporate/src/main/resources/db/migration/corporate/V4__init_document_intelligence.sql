-- Module 2.2 (revived) + retrofit of Module 2.3: Document Intelligence & Knowledge Extraction
-- Engine. Architecture change from the original Module 2.3: instead of every downstream engine
-- (Corporate Event, Order Book, future Management Commentary/News engines) calling Claude
-- directly over raw text - 4x the LLM cost for the same document, and each engine potentially
-- extracting slightly different values for the same fact (order value ₹1,250 Cr vs ₹1,200 Cr) -
-- one canonical extraction pass runs per document, and every downstream engine becomes a
-- deterministic rule engine reading the stored facts/topics/summary. Nobody re-reads the PDF or
-- re-calls the LLM once this stage has run.

-- One extracted business fact, e.g. (document_id, 'orderValue', '1250', 'CRORE'). A generic
-- key-value shape (not a fixed set of typed columns) is deliberate: different document types
-- (order announcements, capacity expansion, financial results, management commentary) produce
-- entirely different fact sets, and new engines (a future Supply Chain or ESG engine) should be
-- able to read facts this table already has without a schema change. fact_type is normalized
-- (lowercased, non-alphanumeric stripped) at write time so a downstream engine's lookup by a
-- known key name isn't defeated by the LLM returning "Order Value" instead of "orderValue" - a
-- real, disclosed limitation of a free-form fact vocabulary rather than a fully closed enum.
CREATE TABLE corporate.document_facts (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         uuid NOT NULL REFERENCES corporate.documents (id) ON DELETE CASCADE,
    fact_type           varchar(100) NOT NULL,
    fact_value          text NOT NULL,
    unit                varchar(30),
    confidence          numeric(5,2) NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_document_facts_confidence CHECK (confidence >= 0 AND confidence <= 100)
);

CREATE INDEX ix_document_facts_document_id ON corporate.document_facts (document_id);
CREATE INDEX ix_document_facts_fact_type ON corporate.document_facts (fact_type);

-- Open-ended tags describing what a document is about (e.g. "Order Win", "Defence", "Radar",
-- "Government"). Deliberately not a fixed enum for the same reason document_facts.fact_type isn't -
-- this is meant to serve engines that don't exist yet.
CREATE TABLE corporate.document_topics (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         uuid NOT NULL REFERENCES corporate.documents (id) ON DELETE CASCADE,
    topic               varchar(100) NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_document_topics_document_id ON corporate.document_topics (document_id);
CREATE INDEX ix_document_topics_topic ON corporate.document_topics (topic);

-- The canonical extraction's document-level synthesis - one row per document (1:1, unlike
-- document_facts/document_topics which are 1:many), kept as its own table rather than columns on
-- corporate.documents to keep "raw collection data" (Module 2.1's concern: status, raw_pdf,
-- extracted_text) separate from "canonical intelligence output" (this module's concern), matching
-- how document_chunks/document_entities were already split out in the V2 migration.
CREATE TABLE corporate.document_summary (
    document_id         uuid PRIMARY KEY REFERENCES corporate.documents (id) ON DELETE CASCADE,
    document_type       varchar(50) NOT NULL,
    sentiment           varchar(10) NOT NULL,
    confidence          numeric(5,2) NOT NULL,
    summary             text NOT NULL,
    extracted_at        timestamptz NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_document_summary_sentiment CHECK (sentiment IN ('POSITIVE', 'NEGATIVE', 'NEUTRAL')),
    CONSTRAINT ck_document_summary_confidence CHECK (confidence >= 0 AND confidence <= 100)
);

-- Generic per-consumer idempotency tracking. Now that multiple independent rule engines
-- (Corporate Event, Order Book, and future engines) all read the SAME KNOWLEDGE_EXTRACTED
-- document, document-level status can no longer mean "has engine X looked at this yet" - that
-- would only work for one consumer. Each engine instead marks its own checkpoint here after
-- processing a document, regardless of whether it produced any output (a document producing zero
-- events, or zero order facts, is a real, final outcome - not a reason to re-process it forever).
CREATE TABLE corporate.document_consumer_checkpoints (
    document_id         uuid NOT NULL REFERENCES corporate.documents (id) ON DELETE CASCADE,
    consumer            varchar(50) NOT NULL,
    processed_at         timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (document_id, consumer)
);

-- EVENTS_EXTRACTED (added in Module 2.3, before this retrofit) no longer fits the document
-- lifecycle - it implied one specific downstream consumer's status, which breaks now that several
-- independent engines consume the same canonical output. Documents previously marked
-- EVENTS_EXTRACTED are reset to PROCESSED so they flow through the new KNOWLEDGE_EXTRACTED stage;
-- their existing corporate.corporate_events rows (produced by the old direct-Claude-call
-- CorporateEventEngine) are left in place as genuine historical output, not deleted - the
-- retrofitted rule-based engine will independently (re)detect events from canonical facts/topics
-- once this document reaches KNOWLEDGE_EXTRACTED.
UPDATE corporate.documents SET status = 'PROCESSED' WHERE status = 'EVENTS_EXTRACTED';

ALTER TABLE corporate.documents DROP CONSTRAINT ck_documents_status;
ALTER TABLE corporate.documents ADD CONSTRAINT ck_documents_status
    CHECK (status IN ('PENDING', 'DOWNLOADED', 'PROCESSED', 'NEEDS_OCR', 'FAILED', 'KNOWLEDGE_EXTRACTED'));
