package com.alphagraph.decision.api;

import java.time.LocalDate;

/**
 * One shared shape for every *_reasons table this package reads back - {@code
 * discovery.lifecycle_reasons}, {@code discovery.convergence_reasons}, and every domain's own
 * {@code *_state_reasons}/{@code *_sequence_reasons} table all share this exact column set
 * (reason_code, optional metric_name/metric_value, optional evidence_date/evidence_reference).
 */
public record ReasonNote(String reasonCode, String metricName, Double metricValue, LocalDate evidenceDate, String evidenceReference) {
}
