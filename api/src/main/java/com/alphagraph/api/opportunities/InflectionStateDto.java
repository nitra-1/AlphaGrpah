package com.alphagraph.api.opportunities;

import java.time.LocalDate;
import java.util.List;

/** Mirrors {@code decision.api.InflectionState} exactly - one Stage 2 inflection row, resolved as of the containing detail's own {@code asOfDate}. */
public record InflectionStateDto(
    String primaryState, String drivingMetric, Double level, Double change, String velocityBand,
    Integer persistence, Double confidence, LocalDate asOfDate, List<ReasonNoteDto> reasons
) {
}
