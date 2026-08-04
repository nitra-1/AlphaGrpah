package com.alphagraph.corporate.knowledge;

import java.util.UUID;

/**
 * One business fact a Stage 2 {@link DocumentExtractor} produced, in the same key-value-unit
 * shape {@code corporate.document_facts} stores - the canonical, engine-agnostic representation
 * every downstream rule engine reads. {@code factType} must already be normalized (see
 * {@link DocumentIntelligenceEngine#normalizeFactType(String)}) before construction.
 *
 * <p>{@code commitmentLevel} is the second, qualitative confidence dimension (LOW/MEDIUM/HIGH/
 * VERY_HIGH, from language strength - "we hope" vs "we expect" vs "orders already secured") -
 * distinct from {@code extractionConfidence} (0-100, how sure the model is it extracted the fact
 * correctly). Null for facts where the distinction doesn't apply (an order value either was or
 * wasn't stated in the text; there's no "hedging" dimension to it) - only forward-looking
 * statements (management guidance) genuinely have both dimensions.
 *
 * <p>{@code factGroup} correlates several facts that belong to the SAME logical record within one
 * document - needed when an extractor can find more than one structured record per document (e.g.
 * ManagementExtractor finding both revenue guidance and margin guidance in one earnings call).
 * Null when a document has at most one instance of what an extractor produces (e.g. OrderExtractor
 * - Order Book Engine's ledger assumes one order per document).
 */
public record ExtractedFact(String factType, String value, String unit, double extractionConfidence, String commitmentLevel, UUID factGroup) {

    public ExtractedFact(String factType, String value, String unit, double extractionConfidence) {
        this(factType, value, unit, extractionConfidence, null, null);
    }
}
