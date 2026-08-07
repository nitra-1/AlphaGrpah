-- Module 2.6 pre-filter retrofit: RssFeedCollector pulls from general "Markets" RSS feeds with no
-- per-company scoping, so every collected article previously went straight to two unconditional
-- Claude calls (Stage 1 classify + NewsExtractor) regardless of whether it was actually about any
-- tracked company - real, disclosed waste at scale. A cheap, deterministic relevance filter
-- (corporate.newsfeed.NewsRelevanceFilter - keyword/alias match against reference.instruments) now
-- runs before insertion: an article matching a tracked instrument still flows automatically
-- (status PROCESSED, unchanged path); an article that does NOT match is stored as PENDING_REVIEW
-- instead of being silently discarded or auto-extracted - the filter's real false-negative risk
-- (a genuine story that doesn't happen to name a company by symbol/alias) is handled by surfacing
-- exactly those filtered-out articles to an admin for a manual decision, not by trying to make
-- keyword matching perfect. DISCARDED is the terminal state for an admin-rejected article.
ALTER TABLE corporate.documents DROP CONSTRAINT ck_documents_status;
ALTER TABLE corporate.documents ADD CONSTRAINT ck_documents_status
    CHECK (status::text = ANY (ARRAY[
        'PENDING', 'DOWNLOADED', 'PROCESSED', 'NEEDS_OCR', 'FAILED', 'KNOWLEDGE_EXTRACTED',
        'PENDING_REVIEW', 'DISCARDED'
    ]::text[]));
