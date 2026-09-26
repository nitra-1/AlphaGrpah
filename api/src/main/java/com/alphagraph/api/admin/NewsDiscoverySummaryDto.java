package com.alphagraph.api.admin;

public record NewsDiscoverySummaryDto(
    int totalProcessed, int totalProcessedPreviousDay,
    int economyRelevantCount, int economyRelevantCountPreviousDay,
    int uniqueEconomicEventCount, int uniqueEconomicEventCountPreviousDay,
    int sectorsImpactedCount, int sectorsImpactedCountPreviousDay,
    int companiesIdentifiedTracked, int companiesIdentifiedUntracked, int companiesIdentifiedPreviousDay
) {
}
