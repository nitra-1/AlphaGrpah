package com.alphagraph.financial.transformation;

import com.alphagraph.common.inflection.VelocityBand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code asOfDate} is the driving observation's own real {@code periodEnd} (or, for
 * {@code NO_CLEAR_SIGNAL}, the latest available metric's {@code periodEnd}) - deliberately never
 * "today" (see {@code FinancialInflectionEngine}'s javadoc). {@code drivingMetric}/{@code level}/
 * {@code change}/{@code velocityBand}/{@code persistence} are the Stage 2 dimensions
 * (docs/007_Stage2_Inflection_Specification.md §2), sourced from whichever
 * {@link FinancialEvidenceObservation} actually drove {@code primaryState}.
 */
record FinancialInflectionResult(
    UUID instrumentId, String symbol, LocalDate asOfDate, FinancialInflectionState primaryState, double confidence,
    FinancialMetric drivingMetric, BigDecimal level, BigDecimal change, VelocityBand velocityBand, int persistence,
    List<ReasonCode> reasons
) {
}
