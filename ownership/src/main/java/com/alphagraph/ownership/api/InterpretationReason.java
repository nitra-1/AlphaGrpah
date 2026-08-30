package com.alphagraph.ownership.api;

/** One persisted piece of evidence backing an {@link InstitutionalInterpretationDetail}. */
public record InterpretationReason(String reasonCode, Double metricValue, String evidenceReference) {
}
