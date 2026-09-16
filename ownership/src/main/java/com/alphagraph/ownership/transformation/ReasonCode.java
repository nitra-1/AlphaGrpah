package com.alphagraph.ownership.transformation;

/**
 * One persisted, human-readable piece of evidence backing a transformation state - never store a
 * conclusion without its reasons. Deliberately a separate copy from
 * {@code ownership.interpretation.ReasonCode} (same shape) - domain packages never share a type
 * across module-internal boundaries in this codebase, matching every other duplicated-lookup
 * precedent (e.g. {@code OwnershipInstrumentLookup} vs {@code market}'s own instrument lookup).
 */
record ReasonCode(String code, Double metricValue, String evidenceReference) {

    static ReasonCode of(String code) {
        return new ReasonCode(code, null, null);
    }

    static ReasonCode of(String code, double metricValue) {
        return new ReasonCode(code, metricValue, null);
    }
}
