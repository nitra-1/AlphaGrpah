package com.alphagraph.corporate.knowledge;

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
 */
public record ExtractedFact(String factType, String value, String unit, double extractionConfidence, String commitmentLevel) {

    public ExtractedFact(String factType, String value, String unit, double extractionConfidence) {
        this(factType, value, unit, extractionConfidence, null);
    }
}
