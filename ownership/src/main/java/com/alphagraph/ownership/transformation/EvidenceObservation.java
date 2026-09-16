package com.alphagraph.ownership.transformation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One metric's computed change/velocity/persistence for one instrument at one quarter transition -
 * exactly the shape persisted to the append-only {@code ownership.transformation_evidence} ledger.
 * {@code priorPeriodEnd}/{@code priorValue}/{@code changePp}/{@code velocityPpPerQuarter} are all
 * null together when this is the metric's first-ever observation for this instrument - a real,
 * lower-confidence but still-evidenced case, not an error (same philosophy
 * {@code financial.engine.FundamentalEngine}'s own missing-prior-period handling documents: "no
 * trend to report without two points, and guessing one from a single period's absolute level would
 * be inventing a trend").
 */
record EvidenceObservation(
    TransformationMetric metric, UUID instrumentId, String symbol, LocalDate periodEnd, LocalDate priorPeriodEnd,
    BigDecimal value, BigDecimal priorValue, BigDecimal changePp, BigDecimal velocityPpPerQuarter,
    int persistenceQuarters, double confidence
) {
}
