-- NewsLinkWriter.write() was a plain INSERT with no dedup guard - safe as long as
-- document_consumer_checkpoints only ever let a document reach it once. That assumption breaks
-- for the entity-graph-link backfill recovery (V13): resetting a document's checkpoint so a
-- previously-unmatched company name can now resolve would also re-insert every link that already
-- matched correctly the first time, since the writer has no way to know a link already exists.
-- No existing duplicates today (confirmed before writing this migration), so this constraint is
-- safe to add now, before any recovery pass runs.
ALTER TABLE corporate.document_instrument_links
    ADD CONSTRAINT ux_document_instrument_links_document_instrument UNIQUE (document_id, instrument_id);
