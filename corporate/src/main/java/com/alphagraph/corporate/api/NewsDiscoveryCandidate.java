package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One {@code corporate.news_discovery_candidates} row - a company recently identified as exposed
 * to a meaningful economic event, tracked (has a real {@code matched_instrument_id} on its own
 * exposure history) or not. Deliberately carries no score/ranking field - see the table's own
 * migration comment. {@code tracked} is computed at read time against {@code reference.instruments}
 * (live, never a status flag this feature writes), same discipline as
 * {@code ownership.deals.DiscoveryReader}'s own already-tracked exclusion.
 */
public record NewsDiscoveryCandidate(
    String symbol, String companyName, UUID securityMasterId, boolean tracked,
    Instant firstSeenAt, Instant lastSeenAt, int exposureCount, String bestDirection, String bestExposureType, String status
) {
}
