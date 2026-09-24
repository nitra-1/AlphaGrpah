package com.alphagraph.api.opportunities;

import java.time.LocalDate;

/** Mirrors {@code decision.api.ReasonNote} exactly. */
public record ReasonNoteDto(String reasonCode, String metricName, Double metricValue, LocalDate evidenceDate, String evidenceReference) {
}
