package com.alphagraph.corporate.api;

/**
 * The 13 corporate event categories the Corporate Event Engine (Module 2.3) detects from document
 * text, per the user-specified worked example (docs/claude.md Phase 2 §Module 2.3).
 */
public enum EventType {
    LARGE_ORDER,
    CAPACITY_EXPANSION,
    NEW_PLANT,
    ACQUISITION,
    MERGER,
    JOINT_VENTURE,
    PLI_APPROVAL,
    PATENT,
    EXPORT_APPROVAL,
    GOVERNMENT_CONTRACT,
    DEBT_RAISING,
    PROMOTER_BUYING,
    PROMOTER_SELLING
}
