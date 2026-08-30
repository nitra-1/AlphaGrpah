package com.alphagraph.ownership.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One individual deal for the Discovery expand-on-click section - the raw deal itself plus its
 * own materiality result if it's been scored yet. The materiality fields are null when the deal
 * hasn't been scored (see {@code ownership.deals.DealMaterialityScoringOrchestrator}), most
 * commonly because its symbol doesn't have 20 trading sessions of price history yet - never a
 * guessed placeholder. {@code isDuplicate} is true when this deal is a confirmed cross-feed
 * BULK/BLOCK duplicate of another row for the same real trade (see V11's migration comment) -
 * still shown here for audit, but excluded from every symbol-level aggregate.
 */
public record DiscoveredDealDetail(
    UUID id, LocalDate dealDate, String clientName, String buySell,
    long quantity, BigDecimal price, BigDecimal dealValue, String dealType, boolean isDuplicate,
    Double materialityScore, String materialityLevel, Double dealToAdtvRatio, String reportedFlowState
) {
}
