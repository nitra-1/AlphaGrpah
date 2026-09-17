package com.alphagraph.market.transformation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One metric's computed change/velocity/persistence for one instrument at one trading day -
 * exactly the shape persisted to the append-only {@code market.transformation_evidence} ledger.
 * {@code priorTradeDate}/{@code priorValue}/{@code change}/{@code velocityPerDay} are all null
 * together when this is the metric's first-ever computable observation for this instrument (not
 * enough trailing history yet for a second data point) - a real, lower-confidence but
 * still-evidenced case, same philosophy as {@code ownership.transformation.EvidenceObservation}.
 */
record MarketEvidenceObservation(
    MarketMetric metric, UUID instrumentId, String symbol, LocalDate tradeDate, LocalDate priorTradeDate,
    BigDecimal value, BigDecimal priorValue, BigDecimal change, BigDecimal velocityPerDay,
    int persistenceDays, double confidence
) {
}
