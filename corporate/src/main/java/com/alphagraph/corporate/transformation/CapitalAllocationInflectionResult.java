package com.alphagraph.corporate.transformation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code asOfDate} is today's date (Clock-based) - Capital Allocation's Stage 1 evidence genuinely
 * is recomputed daily (a rolling 180-day window), unlike Financial's quarterly cadence, so a
 * daily-fresh row here is correct, not a cadence bug (see
 * {@code financial.transformation.FinancialInflectionResult}'s javadoc for the family where a
 * Clock-based date would have been wrong). {@code drivingMetric}/{@code level}/{@code change}/
 * {@code velocityBand}/{@code persistence} are the Stage 2 dimensions
 * (docs/007_Stage2_Inflection_Specification.md §2). {@code level}/{@code change} are plain event
 * counts (never a velocity band - see {@code CapitalAllocationInflectionEngine}'s javadoc).
 */
record CapitalAllocationInflectionResult(
    UUID instrumentId, String symbol, LocalDate asOfDate, CapitalAllocationInflectionState primaryState, double confidence,
    CapitalAllocationMetric drivingMetric, Integer level, Integer change, int persistence,
    List<ReasonCode> reasons
) {
}
