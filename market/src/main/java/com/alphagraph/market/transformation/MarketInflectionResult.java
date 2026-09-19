package com.alphagraph.market.transformation;

import com.alphagraph.common.inflection.VelocityBand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code drivingMetric}/{@code level}/{@code change}/{@code velocityBand}/{@code persistence} are
 * the Stage 2 dimensions (docs/007_Stage2_Inflection_Specification.md §2), sourced from whichever
 * {@link MarketEvidenceObservation} actually drove {@code primaryState}. All null/0 together for
 * {@link MarketInflectionState#NO_CLEAR_SIGNAL} - no single metric drove "nothing happened".
 */
record MarketInflectionResult(
    UUID instrumentId, String symbol, LocalDate asOfDate, MarketInflectionState primaryState, double confidence,
    MarketMetric drivingMetric, BigDecimal level, BigDecimal change, VelocityBand velocityBand, int persistence,
    List<ReasonCode> reasons
) {
}
