package com.alphagraph.intelligence.riskcontradiction;

import java.math.BigDecimal;

/**
 * One persisted, human-readable piece of evidence backing a contradiction state - never store a
 * conclusion without its reasons. Deliberately a separate copy from every domain module's own
 * {@code ReasonCode} (same shape) - domain/intelligence packages never share a type across
 * module-internal boundaries in this codebase. Converted to
 * {@code risk.contradiction.RiskContradictionWriter.ReasonEntry} at the orchestrator's write
 * boundary (that type is owned by {@code risk}, the domain module actually doing the writing).
 */
record ReasonCode(String code, Double metricValue, String evidenceReference) {

    static ReasonCode of(String code) {
        return new ReasonCode(code, null, null);
    }

    static ReasonCode of(String code, double metricValue) {
        return new ReasonCode(code, metricValue, null);
    }

    /** {@code rs.getObject("metric_value")} on a nullable Postgres numeric column returns a BigDecimal, never a Double - reading a reason row back needs an explicit conversion. */
    static ReasonCode fromRow(String code, BigDecimal metricValue, String evidenceReference) {
        return new ReasonCode(code, metricValue == null ? null : metricValue.doubleValue(), evidenceReference);
    }
}
