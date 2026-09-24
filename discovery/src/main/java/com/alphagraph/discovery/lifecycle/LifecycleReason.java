package com.alphagraph.discovery.lifecycle;

import java.time.LocalDate;

/**
 * Deliberately a separate copy from {@code discovery.convergence.ReasonCode} (same "never share a
 * type across packages" convention every domain's own copy already uses) - shaped to match
 * {@code discovery.lifecycle_reasons}'s own columns.
 */
record LifecycleReason(String code, String metricName, Double metricValue, LocalDate evidenceDate, String evidenceReference) {

    static LifecycleReason of(String code) {
        return new LifecycleReason(code, null, null, null, null);
    }

    static LifecycleReason forMetric(String code, String metricName, double metricValue) {
        return new LifecycleReason(code, metricName, metricValue, null, null);
    }

    static LifecycleReason withEvidence(String code, String evidenceReference, LocalDate evidenceDate) {
        return new LifecycleReason(code, null, null, evidenceDate, evidenceReference);
    }
}
