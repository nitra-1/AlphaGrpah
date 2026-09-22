package com.alphagraph.ownership.transformation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One metric's computed change/velocity/persistence for one instrument at one quarter transition -
 * exactly the shape persisted to the append-only {@code ownership.transformation_evidence} ledger.
 * A read-side twin of {@link EvidenceObservation} (same fields, same source table) - kept separate
 * because {@link EvidenceObservation} is Stage 1's own write-time record, and Stage 3's own read
 * path (bounded, point-in-time, ascending-history queries) is structurally different from Stage 1's
 * single-current-quarter compute, matching {@code market.transformation}'s own
 * {@code MarketEvidenceObservation} vs. Stage 1 evidence-record split.
 */
record OwnershipEvidenceObservation(
    TransformationMetric metric, UUID instrumentId, String symbol, LocalDate periodEnd, LocalDate priorPeriodEnd,
    BigDecimal value, BigDecimal priorValue, BigDecimal changePp, BigDecimal velocityPpPerQuarter,
    int persistenceQuarters, double confidence
) {
}
