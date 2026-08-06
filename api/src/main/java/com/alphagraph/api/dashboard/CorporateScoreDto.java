package com.alphagraph.api.dashboard;

/** "Corporate Score" widget row, ranked by corporateScore descending. */
public record CorporateScoreDto(
    String symbol, double corporateScore, String corporateRating,
    Double orderBookScore, Double managementScore, Double newsCatalystScore, int eventNetSignal
) {
}
