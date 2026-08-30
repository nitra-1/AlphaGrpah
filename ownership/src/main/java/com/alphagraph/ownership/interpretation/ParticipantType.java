package com.alphagraph.ownership.interpretation;

/**
 * Deterministic name-pattern classification only - no ML, no fuzzy matching. Deliberately excludes
 * PROMOTER/PROMOTER_GROUP/STRATEGIC_INVESTOR: no promoter/shareholding data source exists anywhere
 * in AlphaGraph, so classifying a participant as a company's promoter from name text alone would
 * be a fabrication, not a real inference - deferred to a future sprint. {@link #UNKNOWN} is a
 * legitimate, expected outcome when no rule confidently matches, not a bug.
 */
public enum ParticipantType {
    MUTUAL_FUND,
    INSURANCE,
    AIF,
    CORPORATE,
    PROP_DESK,
    QUANT_HFT,
    BROKER,
    INDIVIDUAL,
    FPI_FII,
    SOVEREIGN_PENSION_FUND,
    UNKNOWN
}
