package com.alphagraph.intelligence.riskcontradiction;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * No {@code drivingMetric}/{@code level}/{@code change}/{@code velocityBand}/{@code persistence} -
 * unlike every other Stage 2 family, this one's states are boolean composites of other families'
 * already-decided states, not a single banded numeric metric of its own (§13's own trigger table
 * has no "Persistence rule" column either). {@code evidenceCoveragePct}/{@code dataReadiness} are
 * independent of {@code primaryState} - always computed from which of the (up to 6) real reads
 * returned data, even on a firing state.
 */
record RiskContradictionResult(
    UUID instrumentId, String symbol, LocalDate asOfDate, RiskContradictionState primaryState, double confidence,
    int evidenceCoveragePct, RiskContradictionReadiness dataReadiness, List<ReasonCode> reasons
) {
}
