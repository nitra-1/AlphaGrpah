package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One extracted business fact, mirroring {@code corporate.document_facts}
 * (docs/003_Database_Architecture.md §3a). A generic key-value shape rather than fixed typed
 * columns, deliberately - different document types (order announcements, capacity expansion,
 * financial results, management commentary) produce entirely different fact sets, and future
 * engines should be able to read facts this table already has without a schema change.
 * {@code factType} is normalized (lowercased, non-alphanumeric stripped) at write time so a
 * downstream engine's lookup by a known key name isn't defeated by minor LLM key-naming
 * variance. {@code commitmentLevel} is the second, qualitative confidence dimension (LOW/MEDIUM/
 * HIGH/VERY_HIGH, from language strength) - null for facts where it doesn't apply. {@code
 * factGroup} correlates facts belonging to the same logical record within one document (e.g. one
 * management guidance statement, when a document has several) - null when a document has at most
 * one instance of what its extractor produces.
 */
public record DocumentFact(
    UUID id, UUID documentId, String factType, String factValue, String unit, double confidence,
    Instant createdAt, String commitmentLevel, UUID factGroup
) {
}
