package com.alphagraph.corporate.api;

/**
 * What kind of real-world thing a {@code knowledge.entity_master} row names. Deliberately broader
 * than "the tracked instrument universe" - CUSTOMER, THEME, GOVERNMENT_SCHEME, and COMPETITOR
 * entities routinely have no corresponding {@code reference.instruments} row at all.
 */
public enum EntityType {
    COMPANY,
    CUSTOMER,
    THEME,
    GOVERNMENT_SCHEME,
    COMPETITOR,
    SECTOR
}
