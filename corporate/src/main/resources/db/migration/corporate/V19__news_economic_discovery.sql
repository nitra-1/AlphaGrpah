-- News & Economic Discovery rework: replaces the manual PENDING_REVIEW human-triage queue
-- (NewsRelevanceFilter's keyword-match-against-tracked-instruments-only gate) with an automatic
-- classification pipeline that turns every collected article into a structured, time-bound
-- Economic Event -> Sector Impact -> Company Exposure (tracked AND untracked) chain. News never
-- directly modifies the six Decision Engine scores - this is evidence/context only, same
-- discipline as the existing corporate.news_catalyst_scores path this migration does not touch.
--
-- One-time backlog decision (confirmed with the user): the 3,129 PENDING_REVIEW rows older than
-- 5 days are bulk-discarded below: not reprocessed, real LLM cost avoided. The remaining ~408
-- recent rows are left as PROCESSED (backfilled by the reworked NewsFeedLoader's own status logic
-- going forward is irrelevant to already-collected rows) so the next knowledge-extraction run
-- picks them up through the new pipeline exactly like any other document.
UPDATE corporate.documents
SET status = 'DISCARDED'
WHERE source = 'NEWS' AND status = 'PENDING_REVIEW' AND announced_at < now() - interval '5 days';

UPDATE corporate.documents
SET status = 'PROCESSED'
WHERE source = 'NEWS' AND status = 'PENDING_REVIEW';

-- NOT_ECONOMIC is the new deterministic pre-filter's own terminal status (sports/entertainment/
-- celebrity/crime keyword blocklist, corporate.newsfeed.NonEconomicPreFilter) - distinct from
-- DISCARDED (reserved for the old manual-review/backlog-cleanup meaning above) since nothing
-- writes DISCARDED going forward. PENDING_REVIEW stays in the constraint for the existing rows'
-- own history/audit trail even though nothing writes it anymore.
ALTER TABLE corporate.documents DROP CONSTRAINT ck_documents_status;
ALTER TABLE corporate.documents ADD CONSTRAINT ck_documents_status
    CHECK (status::text = ANY (ARRAY[
        'PENDING', 'DOWNLOADED', 'PROCESSED', 'NEEDS_OCR', 'FAILED', 'KNOWLEDGE_EXTRACTED',
        'PENDING_REVIEW', 'DISCARDED', 'NOT_ECONOMIC'
    ]::text[]));

-- One row per document (v1 dedup proxy: cluster_key = normalized theme + calendar date - a
-- pragmatic stand-in for real semantic event clustering, disclosed simplification; still answers
-- "how many distinct source articles support this same underlying event" via a COUNT(*) grouped
-- by cluster_key at read time, which is what actually matters for "don't let 15 articles = 15
-- signals"). expiry_date is a simple rule-driven fixed cutoff (corporate.news.
-- EconomicEventRuleSetLoader), not a continuously-recomputed decay curve - disclosed v1
-- simplification, same "don't over-engineer before outcome evidence exists" discipline as every
-- other rule-driven engine in this codebase.
CREATE TABLE corporate.economic_events (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         uuid NOT NULL REFERENCES corporate.documents (id) ON DELETE CASCADE,
    theme               text NOT NULL,
    economic_relevance  varchar(20) NOT NULL,
    direction           varchar(10) NOT NULL,
    magnitude           varchar(10) NOT NULL,
    confidence          numeric(5,2) NOT NULL,
    horizon             varchar(20) NOT NULL,
    cluster_key         text NOT NULL,
    expiry_date         date NOT NULL,
    computed_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_economic_events_relevance CHECK (economic_relevance IN (
        'ECONOMIC', 'MARKET', 'SECTOR', 'COMPANY', 'COMMODITY', 'REGULATORY', 'GEOPOLITICAL',
        'MACRO', 'NON_ECONOMIC', 'LOW_CONFIDENCE'
    )),
    CONSTRAINT ck_economic_events_direction CHECK (direction IN ('POSITIVE', 'NEGATIVE', 'MIXED', 'NEUTRAL')),
    CONSTRAINT ck_economic_events_magnitude CHECK (magnitude IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_economic_events_horizon CHECK (horizon IN ('SHORT_TERM', 'MEDIUM_TERM', 'LONG_TERM'))
);

CREATE INDEX ix_economic_events_document_id ON corporate.economic_events (document_id);
CREATE INDEX ix_economic_events_cluster_key ON corporate.economic_events (cluster_key);
CREATE INDEX ix_economic_events_expiry_date ON corporate.economic_events (expiry_date);

-- One event can - and often does - produce BOTH positive and negative rows across different
-- sectors ("crude oil up" -> Airlines NEGATIVE, Oil Producers POSITIVE) - never collapsed into one
-- sector-level verdict. sector_id is nullable: a sector name the LLM names that doesn't resolve
-- via reference.instrument.SectorService.findOrCreateByName's own real-instrument-backed sectors
-- table still gets its raw text preserved (sector_name_raw), just without a foreign key.
CREATE TABLE corporate.economic_event_sector_impacts (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        uuid NOT NULL REFERENCES corporate.economic_events (id) ON DELETE CASCADE,
    sector_id       uuid NULL REFERENCES reference.sectors (id),
    sector_name_raw text NOT NULL,
    direction       varchar(10) NOT NULL,
    strength        varchar(10) NOT NULL,
    confidence      numeric(5,2) NOT NULL,
    mechanism       text NULL,
    CONSTRAINT ck_economic_event_sector_impacts_direction CHECK (direction IN ('POSITIVE', 'NEGATIVE', 'MIXED', 'NEUTRAL')),
    CONSTRAINT ck_economic_event_sector_impacts_strength CHECK (strength IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX ix_economic_event_sector_impacts_event_id ON corporate.economic_event_sector_impacts (event_id);
CREATE INDEX ix_economic_event_sector_impacts_sector_id ON corporate.economic_event_sector_impacts (sector_id);

-- match_type makes every company resolution auditable and correctable, never silent - a wrong
-- match is worse than no match (see corporate.news.CompanyResolver's own javadoc for the full
-- 4-step resolution pipeline this column records the outcome of). UNRESOLVED rows are stored here
-- for detail-view visibility but never create/update a news_discovery_candidates row.
CREATE TABLE corporate.economic_event_company_exposures (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id                    uuid NOT NULL REFERENCES corporate.economic_events (id) ON DELETE CASCADE,
    company_name_raw            text NOT NULL,
    matched_instrument_id       uuid NULL,
    matched_security_master_id  uuid NULL REFERENCES reference.security_master (id),
    match_type                  varchar(25) NOT NULL,
    exposure_type               varchar(20) NOT NULL,
    direction                   varchar(10) NOT NULL,
    impact_strength             varchar(10) NOT NULL,
    confidence                  numeric(5,2) NOT NULL,
    reason                      text NULL,
    CONSTRAINT ck_economic_event_company_exposures_match_type CHECK (match_type IN (
        'EXACT_INSTRUMENT', 'EXACT_SECURITY_MASTER', 'ALIAS', 'SAFE_SUBSTRING', 'UNRESOLVED'
    )),
    CONSTRAINT ck_economic_event_company_exposures_exposure_type CHECK (exposure_type IN (
        'DIRECT', 'SUPPLY_CHAIN', 'INPUT_COST', 'DEMAND', 'REGULATORY', 'COMPETITIVE', 'MACRO'
    )),
    CONSTRAINT ck_economic_event_company_exposures_direction CHECK (direction IN ('POSITIVE', 'NEGATIVE', 'MIXED', 'NEUTRAL')),
    CONSTRAINT ck_economic_event_company_exposures_strength CHECK (impact_strength IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX ix_economic_event_company_exposures_event_id ON corporate.economic_event_company_exposures (event_id);
CREATE INDEX ix_economic_event_company_exposures_instrument_id ON corporate.economic_event_company_exposures (matched_instrument_id);
CREATE INDEX ix_economic_event_company_exposures_security_master_id ON corporate.economic_event_company_exposures (matched_security_master_id);

-- Starts empty, grown incrementally as a real unresolved company name is found and confirmed by
-- an admin - never pre-populated via any fuzzy/automated process. Same 3-column shape as
-- ownership.deal_participant_aliases (the one existing precedent for name-alias matching in this
-- codebase), not a new pattern.
CREATE TABLE corporate.news_company_aliases (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol            varchar(20) NOT NULL,
    alias             text NOT NULL,
    normalized_alias  text NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_news_company_aliases_normalized_alias UNIQUE (normalized_alias)
);

-- "Which companies have recently been identified as exposed to meaningful economic events" -
-- deliberately NOT "which is the best investment": no score/ranking column exists or should be
-- added here. status is a real, explicit lifecycle (NEW -> UNDER_OBSERVATION -> PROMOTED |
-- DISMISSED) distinct from ownership.discovery_status (a separate root cause - news exposure, not
-- bulk/block deals) and distinct from Stage 1-5's own tracked-universe evidence chain - promotion
-- to a tracked instrument only ever happens through the existing, unmodified POST
-- /api/v1/admin/instruments flow, never automatically from this table.
CREATE TABLE corporate.news_discovery_candidates (
    symbol               varchar(20) PRIMARY KEY,
    company_name         text NOT NULL,
    security_master_id   uuid NOT NULL REFERENCES reference.security_master (id),
    first_seen_at        timestamptz NOT NULL DEFAULT now(),
    last_seen_at         timestamptz NOT NULL DEFAULT now(),
    exposure_count       integer NOT NULL DEFAULT 1,
    best_direction       varchar(10) NOT NULL,
    best_exposure_type   varchar(20) NOT NULL,
    status               varchar(20) NOT NULL DEFAULT 'NEW',
    CONSTRAINT ck_news_discovery_candidates_direction CHECK (best_direction IN ('POSITIVE', 'NEGATIVE', 'MIXED', 'NEUTRAL')),
    CONSTRAINT ck_news_discovery_candidates_exposure_type CHECK (best_exposure_type IN (
        'DIRECT', 'SUPPLY_CHAIN', 'INPUT_COST', 'DEMAND', 'REGULATORY', 'COMPETITIVE', 'MACRO'
    )),
    CONSTRAINT ck_news_discovery_candidates_status CHECK (status IN ('NEW', 'UNDER_OBSERVATION', 'PROMOTED', 'DISMISSED'))
);
