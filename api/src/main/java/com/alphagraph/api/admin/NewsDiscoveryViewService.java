package com.alphagraph.api.admin;

import com.alphagraph.corporate.api.CompanyExposureDetail;
import com.alphagraph.corporate.api.EconomicEventDetail;
import com.alphagraph.corporate.api.EconomicEventSummary;
import com.alphagraph.corporate.api.NewsDiscoveryCandidate;
import com.alphagraph.corporate.api.NewsDiscoverySummary;
import com.alphagraph.corporate.api.NewsSourceArticle;
import com.alphagraph.corporate.api.SectorImpactDetail;
import com.alphagraph.corporate.api.SectorImpactMapEntry;
import com.alphagraph.corporate.news.EconomicEventReader;
import com.alphagraph.corporate.news.NewsDiscoveryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NewsDiscoveryViewService {

    private final EconomicEventReader reader;
    private final NewsDiscoveryService service;

    public NewsDiscoveryViewService(EconomicEventReader reader, NewsDiscoveryService service) {
        this.reader = reader;
        this.service = service;
    }

    public NewsDiscoverySummaryDto summary() {
        return toDto(reader.summary());
    }

    public List<EconomicEventSummaryDto> latestEvents(int limit) {
        return reader.findLatestEvents(limit).stream().map(NewsDiscoveryViewService::toDto).toList();
    }

    public Optional<EconomicEventDetailDto> eventDetail(UUID eventId) {
        return reader.findEventDetail(eventId).map(NewsDiscoveryViewService::toDto);
    }

    public List<SectorImpactMapEntryDto> sectorImpactMap() {
        return reader.findSectorImpactMap().stream().map(NewsDiscoveryViewService::toDto).toList();
    }

    public List<NewsDiscoveryCandidateDto> candidates() {
        return reader.findCandidates().stream().map(NewsDiscoveryViewService::toDto).toList();
    }

    public boolean observe(String symbol) {
        return service.observe(symbol);
    }

    public boolean dismiss(String symbol) {
        return service.dismiss(symbol);
    }

    private static NewsDiscoverySummaryDto toDto(NewsDiscoverySummary s) {
        return new NewsDiscoverySummaryDto(
            s.totalProcessed(), s.totalProcessedPreviousDay(),
            s.economyRelevantCount(), s.economyRelevantCountPreviousDay(),
            s.uniqueEconomicEventCount(), s.uniqueEconomicEventCountPreviousDay(),
            s.sectorsImpactedCount(), s.sectorsImpactedCountPreviousDay(),
            s.companiesIdentifiedTracked(), s.companiesIdentifiedUntracked(), s.companiesIdentifiedPreviousDay()
        );
    }

    private static EconomicEventSummaryDto toDto(EconomicEventSummary e) {
        return new EconomicEventSummaryDto(
            e.id(), e.theme(), e.economicRelevance(), e.direction(), e.magnitude(),
            e.confidence(), e.horizon(), e.sourceArticleCount(), e.publishedAt(), e.computedAt()
        );
    }

    private static EconomicEventDetailDto toDto(EconomicEventDetail d) {
        return new EconomicEventDetailDto(
            d.id(), d.theme(), d.economicRelevance(), d.direction(), d.magnitude(), d.confidence(), d.horizon(), d.computedAt(),
            d.sectorImpacts().stream().map(NewsDiscoveryViewService::toDto).toList(),
            d.companyExposures().stream().map(NewsDiscoveryViewService::toDto).toList(),
            d.sourceArticles().stream().map(NewsDiscoveryViewService::toDto).toList()
        );
    }

    private static SectorImpactDetailDto toDto(SectorImpactDetail s) {
        return new SectorImpactDetailDto(s.sectorId(), s.sectorName(), s.direction(), s.strength(), s.confidence(), s.mechanism());
    }

    private static CompanyExposureDetailDto toDto(CompanyExposureDetail c) {
        return new CompanyExposureDetailDto(
            c.matchedInstrumentId(), c.matchedSecurityMasterId(), c.matchedSymbol(), c.tracked(),
            c.companyNameRaw(), c.matchType(), c.exposureType(), c.direction(), c.impactStrength(), c.confidence(), c.reason()
        );
    }

    private static NewsSourceArticleDto toDto(NewsSourceArticle a) {
        return new NewsSourceArticleDto(a.documentId(), a.title(), a.sourceUrl(), a.announcedAt());
    }

    private static SectorImpactMapEntryDto toDto(SectorImpactMapEntry e) {
        return new SectorImpactMapEntryDto(e.sectorId(), e.sectorName(), e.direction(), e.strength(), e.contributingEventCount());
    }

    private static NewsDiscoveryCandidateDto toDto(NewsDiscoveryCandidate c) {
        return new NewsDiscoveryCandidateDto(
            c.symbol(), c.companyName(), c.securityMasterId(), c.tracked(), c.firstSeenAt(), c.lastSeenAt(),
            c.exposureCount(), c.bestDirection(), c.bestExposureType(), c.status()
        );
    }
}
