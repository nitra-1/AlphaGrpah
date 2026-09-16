package com.alphagraph.ownership.transformation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record OwnershipTransformationResult(
    UUID instrumentId, String symbol, LocalDate asOfDate, LocalDate latestPeriodEnd, LocalDate priorPeriodEnd,
    TransformationState primaryState, double confidence, int ruleVersion, Instant computedAt, List<ReasonCode> reasons
) {
}
