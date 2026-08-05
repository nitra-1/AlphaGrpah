-- Real bug found during Module 2.7 live verification: document_summary.document_type was
-- varchar(50), but Stage 1's documentType field (corporate.knowledge.DocumentIntelligenceEngine)
-- was never actually constrained to a short enum in its JSON schema - it's free-form text ("a
-- short label for what kind of document this is"). Every document tested before this one
-- happened to produce a label under 50 characters by chance; a compound document covering an
-- order, management guidance, and industry news in one filing produced a longer, genuinely
-- descriptive label and hit the limit. Widened generously rather than re-constraining the LLM to
-- a fixed enum, since a free-form label is the whole point (it doesn't gate any downstream logic
-- the way topics/recommendedExtractors do).
ALTER TABLE corporate.document_summary ALTER COLUMN document_type TYPE varchar(200);
