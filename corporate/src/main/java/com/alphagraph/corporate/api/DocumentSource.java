package com.alphagraph.corporate.api;

/**
 * Which of Phase 2's named document sources (docs/claude.md's Phase 2 architecture) a
 * {@code corporate.documents} row came from. Only EXCHANGE_ANNOUNCEMENT is implemented in
 * Module 2.1 - the others are named here so the schema/domain model doesn't need to change shape
 * when later modules add them.
 */
public enum DocumentSource {
    EXCHANGE_ANNOUNCEMENT,
    QUARTERLY_RESULTS,
    INVESTOR_PRESENTATION,
    CONFERENCE_CALL_TRANSCRIPT,
    ANNUAL_REPORT,
    NEWS
}
