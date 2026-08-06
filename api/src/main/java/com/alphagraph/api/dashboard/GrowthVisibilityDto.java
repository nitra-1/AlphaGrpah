package com.alphagraph.api.dashboard;

/** "Growth Visibility" widget row, ranked by growthVisibilityScore descending. */
public record GrowthVisibilityDto(String symbol, double growthVisibilityScore, String guidanceTrend, String managementCredibility) {
}
