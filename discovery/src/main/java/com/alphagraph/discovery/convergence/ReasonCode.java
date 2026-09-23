package com.alphagraph.discovery.convergence;

import java.time.LocalDate;

/**
 * Deliberately a separate copy from every domain's own {@code ReasonCode} (same "never share a
 * type across module boundaries" convention) - but with 2 extra fields ({@code domain}, {@code
 * sequenceType}) no single-domain reason ever needed, since a Stage 4 reason routinely attributes
 * back to a specific source domain/sequence, matching {@code discovery.convergence_reasons}'s own
 * column shape.
 */
record ReasonCode(String code, String domain, String sequenceType, Double metricValue, LocalDate evidenceDate, String evidenceReference) {

    static ReasonCode of(String code) {
        return new ReasonCode(code, null, null, null, null, null);
    }

    static ReasonCode of(String code, double metricValue) {
        return new ReasonCode(code, null, null, metricValue, null, null);
    }

    static ReasonCode forDomain(String code, ConvergenceDomain domain) {
        return new ReasonCode(code, domain.name(), null, null, null, null);
    }

    static ReasonCode domainActive(String code, ConvergenceDomain domain, String sequenceType, LocalDate evidenceDate) {
        return new ReasonCode(code, domain.name(), sequenceType, null, evidenceDate, sequenceType);
    }
}
