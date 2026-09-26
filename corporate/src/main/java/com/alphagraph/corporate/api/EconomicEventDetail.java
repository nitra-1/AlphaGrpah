package com.alphagraph.corporate.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full detail behind one Economic Event - the event's own fields plus its sector impacts, company exposures, and source articles. */
public record EconomicEventDetail(
    UUID id, String theme, String economicRelevance, String direction, String magnitude,
    double confidence, String horizon, Instant computedAt,
    List<SectorImpactDetail> sectorImpacts, List<CompanyExposureDetail> companyExposures, List<NewsSourceArticle> sourceArticles
) {
}
