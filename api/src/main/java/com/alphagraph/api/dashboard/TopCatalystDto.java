package com.alphagraph.api.dashboard;

/** "Top Catalysts" widget row, ranked by catalystScore descending. */
public record TopCatalystDto(String symbol, double catalystScore, String catalystTrend, int recentCatalystCount) {
}
