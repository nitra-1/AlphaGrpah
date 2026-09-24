package com.alphagraph.api.opportunities;

import java.time.LocalDate;

/** Mirrors {@code decision.api.EvidenceObservation} exactly - one Stage 1 evidence row, resolved as of the containing detail's own {@code asOfDate}. */
public record EvidenceObservationDto(String metricName, LocalDate asOfDate, Double value, Double priorValue, Double change, Double confidence, String source) {
}
