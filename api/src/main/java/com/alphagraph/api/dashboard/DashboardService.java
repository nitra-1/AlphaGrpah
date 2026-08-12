package com.alphagraph.api.dashboard;

import com.alphagraph.corporate.api.NewsImpactDirection;
import com.alphagraph.corporate.commentary.ManagementObservationReader;
import com.alphagraph.corporate.commentary.ManagementSnapshotReader;
import com.alphagraph.corporate.events.CorporateEventReader;
import com.alphagraph.corporate.news.NewsCatalystSnapshotReader;
import com.alphagraph.corporate.news.NewsLinkReader;
import com.alphagraph.corporate.orderbook.OrderBookLedgerReader;
import com.alphagraph.corporate.relationships.EntityReader;
import com.alphagraph.corporate.api.CorporateAction;
import com.alphagraph.corporate.signal.CorporateScoreReader;
import com.alphagraph.intelligence.priceadjustment.PriceAdjustmentEngine;
import com.alphagraph.intelligence.priceadjustment.PriceAdjustmentService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Module 2.10: Decision Intelligence API Layer. Assembles dashboard DTOs by calling the
 * already-built corporate-module readers directly (per docs/004_API_Architecture.md §5 - the api
 * module assembles DTOs from domain modules' own readers, it doesn't re-implement queries against
 * their tables), then converts each domain record into the lightweight, dashboard-shaped DTO a
 * widget actually wants (e.g. a resolved customer name, never a raw entity id).
 */
@Service
public class DashboardService {

    private static final int DEFAULT_LOOKBACK_DAYS = 7;

    private final OrderBookLedgerReader orderBookLedgerReader;
    private final CorporateEventReader corporateEventReader;
    private final ManagementObservationReader managementObservationReader;
    private final NewsLinkReader newsLinkReader;
    private final NewsCatalystSnapshotReader newsCatalystSnapshotReader;
    private final ManagementSnapshotReader managementSnapshotReader;
    private final CorporateScoreReader corporateScoreReader;
    private final EntityReader entityReader;
    private final PriceAdjustmentService priceAdjustmentService;
    private final PriceAdjustmentEngine priceAdjustmentEngine;

    public DashboardService(
        OrderBookLedgerReader orderBookLedgerReader, CorporateEventReader corporateEventReader,
        ManagementObservationReader managementObservationReader, NewsLinkReader newsLinkReader,
        NewsCatalystSnapshotReader newsCatalystSnapshotReader, ManagementSnapshotReader managementSnapshotReader,
        CorporateScoreReader corporateScoreReader, EntityReader entityReader,
        PriceAdjustmentService priceAdjustmentService, PriceAdjustmentEngine priceAdjustmentEngine
    ) {
        this.orderBookLedgerReader = orderBookLedgerReader;
        this.corporateEventReader = corporateEventReader;
        this.managementObservationReader = managementObservationReader;
        this.newsLinkReader = newsLinkReader;
        this.newsCatalystSnapshotReader = newsCatalystSnapshotReader;
        this.managementSnapshotReader = managementSnapshotReader;
        this.corporateScoreReader = corporateScoreReader;
        this.entityReader = entityReader;
        this.priceAdjustmentService = priceAdjustmentService;
        this.priceAdjustmentEngine = priceAdjustmentEngine;
    }

    public List<BiggestOrderDto> biggestOrders(int lookbackDays) {
        return orderBookLedgerReader.findRecentAcrossAllInstruments(lookbackDays).stream()
            .map(entry -> new BiggestOrderDto(
                entry.symbol(), entry.orderValueCrore(),
                entry.customerEntityId() == null ? null : entityReader.findCanonicalName(entry.customerEntityId()).orElse(null),
                entry.lifecycleStage().name(), entry.detectedAt()
            ))
            .toList();
    }

    public List<CorporateEventDto> corporateEvents(int lookbackDays) {
        return corporateEventReader.findRecentAcrossAllInstruments(lookbackDays).stream()
            .map(e -> new CorporateEventDto(e.symbol(), e.eventType().name(), e.category(), e.summary(), e.revenueImpact().name(), e.signal().name(), e.extractedAt()))
            .toList();
    }

    public List<GuidanceChangeDto> guidanceChanges(int lookbackDays) {
        return managementObservationReader.findRecentAcrossAllInstruments(lookbackDays).stream()
            .map(o -> new GuidanceChangeDto(o.symbol(), o.metricType(), o.guidanceValue(), o.guidancePeriod(), o.direction().name(), o.commitmentLevel().name(), o.observedAt()))
            .toList();
    }

    public List<NewsItemDto> positiveNews(int lookbackDays) {
        return newsByDirection(NewsImpactDirection.POSITIVE, lookbackDays);
    }

    public List<NewsItemDto> negativeNews(int lookbackDays) {
        return newsByDirection(NewsImpactDirection.NEGATIVE, lookbackDays);
    }

    private List<NewsItemDto> newsByDirection(NewsImpactDirection direction, int lookbackDays) {
        return newsLinkReader.findRecentByDirection(direction, lookbackDays).stream()
            .map(link -> new NewsItemDto(link.symbol(), link.direction().name(), link.signal(), link.impactSummary(), link.announcedAt()))
            .toList();
    }

    public List<TopCatalystDto> topCatalysts() {
        return newsCatalystSnapshotReader.findAllLatest().stream()
            .map(s -> new TopCatalystDto(s.symbol(), s.catalystScore(), s.catalystTrend().name(), s.recentCatalystCount()))
            .toList();
    }

    public List<GrowthVisibilityDto> growthVisibility() {
        return managementSnapshotReader.findAllLatest().stream()
            .map(s -> new GrowthVisibilityDto(s.symbol(), s.growthVisibilityScore(), s.guidanceTrend().name(), s.managementCredibility().name()))
            .toList();
    }

    public List<CorporateScoreDto> corporateScores() {
        return corporateScoreReader.findAllLatest().stream()
            .map(s -> new CorporateScoreDto(s.symbol(), s.corporateScore(), s.corporateRating().name(), s.orderBookScore(), s.managementScore(), s.newsCatalystScore(), s.eventNetSignal()))
            .toList();
    }

    /**
     * BONUS/SPLIT actions in the last N days and the price-adjustment factor each one produces -
     * the explicit "announce" surface: a user sees exactly which instruments' historical prices
     * are now back-adjusted and why, rather than a chart silently changing shape underneath them.
     */
    public List<PriceAdjustmentDto> priceAdjustments(int lookbackDays) {
        return priceAdjustmentService.recentPriceAffectingActions(lookbackDays).stream()
            .map(this::toPriceAdjustmentDto)
            .toList();
    }

    private PriceAdjustmentDto toPriceAdjustmentDto(CorporateAction action) {
        return new PriceAdjustmentDto(
            action.symbol(), action.actionType(), action.exDate(),
            action.ratioNumerator(), action.ratioDenominator(), priceAdjustmentEngine.factorFor(action)
        );
    }

    public DashboardSummaryDto summary() {
        return new DashboardSummaryDto(
            biggestOrders(1), corporateEvents(DEFAULT_LOOKBACK_DAYS), guidanceChanges(DEFAULT_LOOKBACK_DAYS),
            positiveNews(DEFAULT_LOOKBACK_DAYS), negativeNews(DEFAULT_LOOKBACK_DAYS),
            topCatalysts(), growthVisibility(), corporateScores(), priceAdjustments(DEFAULT_LOOKBACK_DAYS)
        );
    }
}
