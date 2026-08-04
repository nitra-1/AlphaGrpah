package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Layer 1: one forward-looking management statement, mirroring
 * {@code corporate.management_observations} (docs/003_Database_Architecture.md §3a) - immutable,
 * one document, one observation, never revised. {@code metricType} is deliberately a free string,
 * not a CHECK-constrained enum, same reasoning as {@code CorporateEvent.category}: it's what the
 * extraction prompt asks for (Revenue Guidance, Margin Guidance, Capex, Demand, Pricing,
 * Competition, Hiring, Exports, Risk), not a promise no other value can appear.
 * {@code guidanceValueNumeric} is null when the statement is qualitative prose rather than a
 * parseable figure (most Demand/Pricing/Competition/Risk commentary) - a real, honest absence, not
 * a parsing failure.
 */
public record ManagementObservation(
    UUID id, UUID documentId, UUID instrumentId, String symbol, String metricType,
    String guidanceValue, Double guidanceValueNumeric, String guidancePeriod,
    GuidanceDirection direction, String signal, CommitmentLevel commitmentLevel,
    double extractionConfidence, Instant observedAt
) {
}
