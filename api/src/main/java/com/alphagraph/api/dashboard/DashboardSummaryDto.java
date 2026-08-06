package com.alphagraph.api.dashboard;

import java.util.List;

/** The combined single-call view of every widget - convenience for a client that wants the whole dashboard in one request. */
public record DashboardSummaryDto(
    List<BiggestOrderDto> biggestOrders, List<CorporateEventDto> corporateEvents, List<GuidanceChangeDto> guidanceChanges,
    List<NewsItemDto> positiveNews, List<NewsItemDto> negativeNews, List<TopCatalystDto> topCatalysts,
    List<GrowthVisibilityDto> growthVisibility, List<CorporateScoreDto> corporateScores
) {
}
