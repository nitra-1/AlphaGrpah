package com.alphagraph.decision.api;

import java.time.LocalDate;
import java.util.List;

/** One Stage 2 inflection row for one domain, resolved as of a given anchor date (never an unconditional "latest"). */
public record InflectionState(
    String primaryState, String drivingMetric, Double level, Double change, String velocityBand,
    Integer persistence, Double confidence, LocalDate asOfDate, List<ReasonNote> reasons
) {
}
