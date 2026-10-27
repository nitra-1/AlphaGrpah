-- Cost controls for exchange-announcement extraction (results-season days cost ~$9 each in October:
-- one filing is often attached to several separate announcements, each stored and sent to Claude
-- in full, and a flood of large filings had no ceiling).
--
-- text_hash: md5 of extracted_text (same function the application computes, UTF-8), so an
-- announcement whose attachment text is identical to one already extracted for the same symbol can
-- be recognised without re-sending it to an LLM. duplicate_of_document_id points at the document
-- that was actually extracted - the repeat gets no facts of its own, which also stops the same
-- filing's facts being counted once per announcement.
ALTER TABLE corporate.documents
    ADD COLUMN text_hash varchar(32),
    ADD COLUMN duplicate_of_document_id uuid REFERENCES corporate.documents (id);

UPDATE corporate.documents SET text_hash = md5(extracted_text) WHERE extracted_text IS NOT NULL;

CREATE INDEX ix_documents_text_hash ON corporate.documents (source, instrument_id, text_hash)
    WHERE text_hash IS NOT NULL;

-- How much announcement text has been sent for extraction today, per source (India calendar day).
-- Backs the daily cap in corporate.knowledge.AnnouncementTextBudget; kept in the database so an app
-- restart can't hand back a fresh allowance. Query it directly to see the day's usage:
--   SELECT * FROM corporate.claude_text_usage ORDER BY usage_date DESC;
CREATE TABLE corporate.claude_text_usage (
    usage_date  date        NOT NULL,
    source      varchar(30) NOT NULL,
    chars_used  bigint      NOT NULL,
    PRIMARY KEY (usage_date, source)
);
