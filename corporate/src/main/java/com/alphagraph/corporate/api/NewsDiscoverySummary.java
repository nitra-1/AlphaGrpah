package com.alphagraph.corporate.api;

/**
 * The 5 stat-card numbers for the News & Economic Discovery dashboard, each paired with
 * yesterday's same-window count so the UI can show a vs-previous-day delta without a second
 * round trip. "Today" is the trailing 24 hours from read time, not calendar-day - matches how
 * every other admin dashboard stat in this codebase treats a rolling window.
 */
public record NewsDiscoverySummary(
    int totalProcessed, int totalProcessedPreviousDay,
    int economyRelevantCount, int economyRelevantCountPreviousDay,
    int uniqueEconomicEventCount, int uniqueEconomicEventCountPreviousDay,
    int sectorsImpactedCount, int sectorsImpactedCountPreviousDay,
    int companiesIdentifiedTracked, int companiesIdentifiedUntracked,
    int companiesIdentifiedPreviousDay
) {
}
