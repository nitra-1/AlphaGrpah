package com.alphagraph.corporate.transformation;

/**
 * One persisted, human-readable piece of evidence backing an inflection state - never store a
 * conclusion without its reasons. Deliberately a separate copy from
 * {@code market.transformation.ReasonCode}/{@code financial.transformation.ReasonCode} (same
 * shape) - domain packages never share a type across module-internal boundaries in this codebase.
 */
record ReasonCode(String code, Double metricValue, String evidenceReference) {

    static ReasonCode of(String code) {
        return new ReasonCode(code, null, null);
    }

    static ReasonCode of(String code, double metricValue) {
        return new ReasonCode(code, metricValue, null);
    }
}
