package com.alphagraph.ownership.api;

import java.time.LocalDate;

/**
 * One untracked symbol with real bulk/block deal activity - aggregated at read time from
 * {@code ownership.discovered_deals} for the admin Discovery review page. {@code securityName}
 * may be null (display only, never guaranteed - see {@code ownership.deals.RawDealRow}).
 */
public record DiscoveryCandidate(
    String symbol, String securityName, int dealCount, int distinctBuyers,
    long totalQuantity, LocalDate firstDealDate, LocalDate latestDealDate
) {
}
