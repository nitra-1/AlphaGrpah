-- Retrofit: Document Understanding (Stage 1, lean) -> Routing -> Specialized Extraction
-- (Stage 2, per-domain) -> Canonical Facts (Stage 3, normalization). Stage 1 no longer extracts
-- business facts itself - it only classifies/tags/summarizes and recommends which Stage 2
-- extractors should run. document_facts is now populated exclusively by Stage 2 extractors
-- (corporate.knowledge.DocumentExtractor implementations), but its shape doesn't change - Stage 3
-- normalization means every extractor's raw output still lands in this same table, so no
-- downstream reader (OrderBookLedgerParser, ...) needed to change at all.

-- commitment_level is the second, qualitative confidence dimension a Stage 2 extractor can
-- attach to a fact: how strongly management is committing to a forward-looking statement
-- ("we hope" = LOW, "we expect" = MEDIUM, "we are confident" = HIGH, "orders already secured" =
-- VERY_HIGH), distinct from the existing numeric confidence column (how sure the model is it
-- extracted the fact correctly). Nullable - only forward-looking facts (management guidance)
-- genuinely have a hedging dimension; a stated order value either was or wasn't in the text.
ALTER TABLE corporate.document_facts ADD COLUMN commitment_level varchar(10);
ALTER TABLE corporate.document_facts ADD CONSTRAINT ck_document_facts_commitment_level
    CHECK (commitment_level IS NULL OR commitment_level IN ('LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH'));
