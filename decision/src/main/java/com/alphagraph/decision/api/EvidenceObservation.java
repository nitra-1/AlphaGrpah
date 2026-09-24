package com.alphagraph.decision.api;

import java.time.LocalDate;

/**
 * One Stage 1 evidence row for one domain/metric, resolved as of a given anchor date (never an
 * unconditional "latest") - the base of the Opportunity Detail page's causal chain: Evidence ->
 * Inflection -> Sequence -> Convergence -> Lifecycle.
 */
public record EvidenceObservation(String metricName, LocalDate asOfDate, Double value, Double priorValue, Double change, Double confidence, String source) {
}
