package com.alphagraph.api.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EconomicEventDetailDto(
    UUID id, String theme, String economicRelevance, String direction, String magnitude,
    double confidence, String horizon, Instant computedAt,
    List<SectorImpactDetailDto> sectorImpacts, List<CompanyExposureDetailDto> companyExposures, List<NewsSourceArticleDto> sourceArticles
) {
}
