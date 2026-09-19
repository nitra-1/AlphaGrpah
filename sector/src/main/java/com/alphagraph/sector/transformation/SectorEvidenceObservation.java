package com.alphagraph.sector.transformation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One metric's computed change/persistence for one instrument as of one real evidence date. Null
 * together with {@code priorAsOfDate}/{@code priorValue} when this is the metric's first-ever
 * observation for this instrument, same philosophy as
 * {@code ownership.transformation.EvidenceObservation}.
 */
record SectorEvidenceObservation(
    SectorMetric metric, UUID instrumentId, String symbol, LocalDate asOfDate, LocalDate priorAsOfDate,
    BigDecimal value, BigDecimal priorValue, BigDecimal change, int persistenceDays, double confidence
) {
}
