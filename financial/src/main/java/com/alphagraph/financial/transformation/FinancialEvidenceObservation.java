package com.alphagraph.financial.transformation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One metric's computed change/velocity/persistence for one instrument at one quarter transition.
 * {@code comparatorUsed} discloses whether {@code change}/{@code velocityPerQuarter} compare
 * against the same quarter one year back ({@code YOY}, preferred whenever that real point exists
 * in the 5-quarter window) or only the immediately preceding quarter ({@code QOQ_ONLY}, the
 * fallback) - never silently picked. Null together with {@code priorPeriodEnd}/{@code priorValue}
 * when this is the metric's first-ever observation for this instrument, same philosophy as
 * {@code ownership.transformation.EvidenceObservation}.
 */
record FinancialEvidenceObservation(
    FinancialMetric metric, UUID instrumentId, String symbol, LocalDate periodEnd, LocalDate priorPeriodEnd,
    BigDecimal value, BigDecimal priorValue, BigDecimal change, BigDecimal velocityPerQuarter,
    int persistenceQuarters, double confidence, String comparatorUsed
) {
}
