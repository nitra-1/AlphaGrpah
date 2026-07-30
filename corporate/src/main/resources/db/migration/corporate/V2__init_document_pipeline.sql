-- Module 2.1: Document Collection Framework - the first Phase 2 schema. Unlike every Phase 1
-- table (rows), these hold documents: raw PDF bytes, extracted text, chunked/embedded/entity-
-- tagged derivatives.
--
-- document_chunks.embedding is a plain real[] rather than pgvector's vector type - pgvector isn't
-- installed on this project's real local Postgres (verified: not even in
-- pg_available_extensions), and this system's Postgres install is shared with other local
-- projects, so installing an unofficial precompiled Windows binary into it wasn't an acceptable
-- risk (docker-compose's postgres image was switched to pgvector/pgvector:pg16 in anticipation,
-- but nothing here depends on that yet since docker isn't available in this dev environment
-- either). real[] stores the same 384 floats and is queryable with a hand-written cosine
-- similarity expression; it just has no ANN index the way pgvector's ivfflat/hnsw would. Revisit
-- once pgvector is genuinely available wherever this runs.

-- One collected document (currently only EXCHANGE_ANNOUNCEMENT - see corporate.api.DocumentSource
-- for the other 5 named Phase 2 sources this schema already accommodates by column shape).
-- external_id is the source's own stable identifier (NSE's seq_id for announcements) - the real
-- upsert key, since a source could in principle republish the same announcement under a new file
-- name. raw_pdf/extracted_text are nullable because status tracks how far a document has actually
-- progressed through Download -> OCR/Parse -> Extract -> Chunk -> Entity Extraction -> Embeddings;
-- a PENDING row has neither yet.
CREATE TABLE corporate.documents (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id       uuid NOT NULL,
    symbol              varchar(20) NOT NULL,
    source              varchar(30) NOT NULL,
    external_id         varchar(50) NOT NULL,
    category            varchar(100),
    title               text,
    source_url          text NOT NULL,
    announced_at        timestamptz NOT NULL,
    raw_pdf             bytea,
    file_size_bytes     bigint,
    extracted_text      text,
    status              varchar(20) NOT NULL DEFAULT 'PENDING',
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_documents_source CHECK (source IN (
        'EXCHANGE_ANNOUNCEMENT', 'QUARTERLY_RESULTS', 'INVESTOR_PRESENTATION',
        'CONFERENCE_CALL_TRANSCRIPT', 'ANNUAL_REPORT', 'NEWS'
    )),
    CONSTRAINT ck_documents_status CHECK (status IN ('PENDING', 'DOWNLOADED', 'PROCESSED', 'NEEDS_OCR', 'FAILED')),
    CONSTRAINT ux_documents_source_external_id UNIQUE (source, external_id)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_documents_instrument_id ON corporate.documents (instrument_id);
CREATE INDEX ix_documents_status ON corporate.documents (status);
CREATE INDEX ix_documents_announced_at ON corporate.documents (announced_at);

CREATE TRIGGER trg_documents_updated_at
    BEFORE UPDATE ON corporate.documents
    FOR EACH ROW EXECUTE FUNCTION corporate.set_updated_at();

-- One chunk of a processed document's text, with its embedding. 384 dimensions matches the
-- sidecar's embedding model (sentence-transformers/all-MiniLM-L6-v2) - changing embedding models
-- later means a new migration, not a schema that tries to stay model-agnostic.
CREATE TABLE corporate.document_chunks (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         uuid NOT NULL REFERENCES corporate.documents (id) ON DELETE CASCADE,
    chunk_index         integer NOT NULL,
    chunk_text          text NOT NULL,
    word_count          integer NOT NULL,
    embedding           real[] NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_document_chunks_document_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX ix_document_chunks_document_id ON corporate.document_chunks (document_id);

-- Named entities (spaCy's default English NER labels - ORG, PERSON, GPE, DATE, MONEY, ...)
-- extracted per chunk. chunk_id may be null if a future source extracts document-level entities
-- without going through chunking (e.g. structured metadata), though nothing populates that path yet.
CREATE TABLE corporate.document_entities (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         uuid NOT NULL REFERENCES corporate.documents (id) ON DELETE CASCADE,
    chunk_id            uuid REFERENCES corporate.document_chunks (id) ON DELETE CASCADE,
    entity_text         text NOT NULL,
    entity_type         varchar(20) NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_document_entities_document_id ON corporate.document_entities (document_id);
CREATE INDEX ix_document_entities_entity_type ON corporate.document_entities (entity_type);
