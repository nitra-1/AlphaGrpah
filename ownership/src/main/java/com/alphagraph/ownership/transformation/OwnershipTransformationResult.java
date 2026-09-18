package com.alphagraph.ownership.transformation;

import com.alphagraph.common.inflection.VelocityBand;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code drivingMetric}/{@code level}/{@code change}/{@code velocityBand}/{@code persistence} are
 * the Stage 2 dimensions (docs/007_Stage2_Inflection_Specification.md §2), sourced from whichever
 * {@link EvidenceObservation} actually drove {@code primaryState} - see
 * {@code OwnershipTransformationEngine}'s driving-metric selection. All null/0 together for
 * {@link TransformationState#NO_CLEAR_SIGNAL} - no single metric drove "nothing happened", so none
 * is guessed.
 */
record OwnershipTransformationResult(
    UUID instrumentId, String symbol, LocalDate asOfDate, LocalDate latestPeriodEnd, LocalDate priorPeriodEnd,
    TransformationState primaryState, double confidence, int ruleVersion, Instant computedAt, List<ReasonCode> reasons,
    TransformationMetric drivingMetric, BigDecimal level, BigDecimal change, VelocityBand velocityBand, int persistence
) {
}
