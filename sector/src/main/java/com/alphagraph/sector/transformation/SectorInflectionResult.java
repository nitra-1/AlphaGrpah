package com.alphagraph.sector.transformation;

import com.alphagraph.common.inflection.VelocityBand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code asOfDate} is the driving observation's own real evidence date (or, for
 * {@code NO_CLEAR_SIGNAL}, the latest available metric's date) - deliberately never "today" (see
 * {@code SectorInflectionEngine}'s javadoc). {@code drivingMetric}/{@code level}/{@code change}/
 * {@code velocityBand}/{@code persistence} are the Stage 2 dimensions
 * (docs/007_Stage2_Inflection_Specification.md §2). {@code evidenceCoveragePct}/
 * {@code dataReadiness} are independent of {@code primaryState} - always computed from which of
 * the 3 source metrics have real evidence, even on a firing state.
 */
record SectorInflectionResult(
    UUID instrumentId, String symbol, LocalDate asOfDate, SectorInflectionState primaryState, double confidence,
    SectorMetric drivingMetric, BigDecimal level, BigDecimal change, VelocityBand velocityBand, int persistence,
    int evidenceCoveragePct, SectorDataReadiness dataReadiness, List<ReasonCode> reasons
) {
}
