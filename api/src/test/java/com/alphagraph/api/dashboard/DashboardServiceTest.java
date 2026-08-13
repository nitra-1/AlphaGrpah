package com.alphagraph.api.dashboard;

import com.alphagraph.corporate.api.CommitmentLevel;
import com.alphagraph.corporate.api.CorporateAction;
import com.alphagraph.corporate.api.CorporateEvent;
import com.alphagraph.corporate.api.CorporateRating;
import com.alphagraph.corporate.api.CorporateScore;
import com.alphagraph.corporate.api.EventSignal;
import com.alphagraph.corporate.api.EventType;
import com.alphagraph.corporate.api.GuidanceDirection;
import com.alphagraph.corporate.api.GuidanceTrend;
import com.alphagraph.corporate.api.ManagementCommentarySnapshot;
import com.alphagraph.corporate.api.ManagementCredibility;
import com.alphagraph.corporate.api.ManagementObservation;
import com.alphagraph.corporate.api.NewsCatalystSnapshot;
import com.alphagraph.corporate.api.NewsCatalystTrend;
import com.alphagraph.corporate.api.NewsImpactDirection;
import com.alphagraph.corporate.api.NewsInstrumentLink;
import com.alphagraph.corporate.api.OrderBookEntry;
import com.alphagraph.corporate.api.OrderLifecycleStage;
import com.alphagraph.corporate.api.RevenueImpact;
import com.alphagraph.corporate.commentary.ManagementObservationReader;
import com.alphagraph.corporate.commentary.ManagementSnapshotReader;
import com.alphagraph.corporate.events.CorporateEventReader;
import com.alphagraph.corporate.news.NewsCatalystSnapshotReader;
import com.alphagraph.corporate.news.NewsLinkReader;
import com.alphagraph.corporate.orderbook.OrderBookLedgerReader;
import com.alphagraph.corporate.relationships.EntityReader;
import com.alphagraph.corporate.signal.CorporateScoreReader;
import com.alphagraph.intelligence.priceadjustment.PriceAdjustmentEngine;
import com.alphagraph.intelligence.priceadjustment.PriceAdjustmentService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private final OrderBookLedgerReader orderBookLedgerReader = mock(OrderBookLedgerReader.class);
    private final CorporateEventReader corporateEventReader = mock(CorporateEventReader.class);
    private final ManagementObservationReader managementObservationReader = mock(ManagementObservationReader.class);
    private final NewsLinkReader newsLinkReader = mock(NewsLinkReader.class);
    private final NewsCatalystSnapshotReader newsCatalystSnapshotReader = mock(NewsCatalystSnapshotReader.class);
    private final ManagementSnapshotReader managementSnapshotReader = mock(ManagementSnapshotReader.class);
    private final CorporateScoreReader corporateScoreReader = mock(CorporateScoreReader.class);
    private final EntityReader entityReader = mock(EntityReader.class);
    private final PriceAdjustmentService priceAdjustmentService = mock(PriceAdjustmentService.class);
    private final PriceAdjustmentEngine priceAdjustmentEngine = mock(PriceAdjustmentEngine.class);

    private final DashboardService service = new DashboardService(
        orderBookLedgerReader, corporateEventReader, managementObservationReader, newsLinkReader,
        newsCatalystSnapshotReader, managementSnapshotReader, corporateScoreReader, entityReader,
        priceAdjustmentService, priceAdjustmentEngine
    );

    @Test
    void biggestOrdersResolvesCustomerEntityIdToName() {
        UUID customerEntityId = UUID.randomUUID();
        when(entityReader.findCanonicalName(customerEntityId)).thenReturn(Optional.of("Ministry of Defence"));
        OrderBookEntry entry = new OrderBookEntry(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BEL", customerEntityId, 2800.0, "Electronics",
            "2026", "2029", null, null, null, OrderLifecycleStage.TENDER_WIN, Instant.parse("2026-06-01T00:00:00Z")
        );
        when(orderBookLedgerReader.findRecentAcrossAllInstruments(1)).thenReturn(List.of(entry));

        List<BiggestOrderDto> result = service.biggestOrders(1);

        assertThat(result).containsExactly(new BiggestOrderDto("BEL", 2800.0, "Ministry of Defence", "TENDER_WIN", Instant.parse("2026-06-01T00:00:00Z")));
    }

    @Test
    void biggestOrdersWithNoCustomerEntityLeavesNameNull() {
        OrderBookEntry entry = new OrderBookEntry(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BEL", null, 500.0, null,
            null, null, null, null, null, OrderLifecycleStage.NEW_ORDER, Instant.now()
        );
        when(orderBookLedgerReader.findRecentAcrossAllInstruments(1)).thenReturn(List.of(entry));

        assertThat(service.biggestOrders(1).get(0).customerName()).isNull();
    }

    @Test
    void corporateEventsMapsEnumsToNames() {
        UUID id = UUID.randomUUID();
        CorporateEvent event = new CorporateEvent(
            id, UUID.randomUUID(), UUID.randomUUID(), "INFY", EventType.LARGE_ORDER, "Category", "Summary",
            85.0, null, RevenueImpact.HIGH, EventSignal.POSITIVE, 1, Instant.now()
        );
        when(corporateEventReader.findRecentAcrossAllInstruments(7)).thenReturn(List.of(event));

        CorporateEventDto dto = service.corporateEvents(7).get(0);

        assertThat(dto.symbol()).isEqualTo("INFY");
        assertThat(dto.eventType()).isEqualTo("LARGE_ORDER");
        assertThat(dto.revenueImpact()).isEqualTo("HIGH");
        assertThat(dto.signal()).isEqualTo("POSITIVE");
    }

    @Test
    void positiveAndNegativeNewsDelegateToCorrectDirection() {
        NewsInstrumentLink positiveLink = new NewsInstrumentLink(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "TCS", NewsImpactDirection.POSITIVE,
            "Signal", "Summary", 90.0, Instant.now()
        );
        when(newsLinkReader.findRecentByDirection(NewsImpactDirection.POSITIVE, 7)).thenReturn(List.of(positiveLink));
        when(newsLinkReader.findRecentByDirection(NewsImpactDirection.NEGATIVE, 7)).thenReturn(List.of());

        assertThat(service.positiveNews(7)).hasSize(1);
        assertThat(service.negativeNews(7)).isEmpty();
        verify(newsLinkReader).findRecentByDirection(eq(NewsImpactDirection.POSITIVE), eq(7));
        verify(newsLinkReader).findRecentByDirection(eq(NewsImpactDirection.NEGATIVE), eq(7));
    }

    @Test
    void topCatalystsMapsSnapshotFields() {
        when(newsCatalystSnapshotReader.findAllLatest()).thenReturn(List.of(
            new NewsCatalystSnapshot(UUID.randomUUID(), "HDFCBANK", LocalDate.of(2026, 6, 1), 45.0, NewsCatalystTrend.POSITIVE, 2, 60.0, 1, Instant.now())
        ));

        TopCatalystDto dto = service.topCatalysts().get(0);

        assertThat(dto).isEqualTo(new TopCatalystDto("HDFCBANK", 45.0, "POSITIVE", 2));
    }

    @Test
    void growthVisibilityMapsSnapshotFields() {
        when(managementSnapshotReader.findAllLatest()).thenReturn(List.of(
            new ManagementCommentarySnapshot(UUID.randomUUID(), "TCS", LocalDate.of(2026, 6, 1), 70.0, GuidanceTrend.UPGRADING, ManagementCredibility.HIGH, 80.0, 1, Instant.now())
        ));

        GrowthVisibilityDto dto = service.growthVisibility().get(0);

        assertThat(dto).isEqualTo(new GrowthVisibilityDto("TCS", 70.0, "UPGRADING", "HIGH"));
    }

    @Test
    void corporateScoresMapsAllComponentScores() {
        when(corporateScoreReader.findAllLatest()).thenReturn(List.of(
            new CorporateScore(UUID.randomUUID(), "INFY", LocalDate.of(2026, 6, 1), 60.0, CorporateRating.NEUTRAL, 80.0, null, null, 2, 55.0, 1, Instant.now())
        ));

        CorporateScoreDto dto = service.corporateScores().get(0);

        assertThat(dto).isEqualTo(new CorporateScoreDto("INFY", 60.0, "NEUTRAL", 80.0, null, null, 2));
    }

    @Test
    void guidanceChangesMapsObservationFields() {
        ManagementObservation observation = new ManagementObservation(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "TCS", "REVENUE_GUIDANCE", "30%", 30.0,
            "next two years", GuidanceDirection.POSITIVE, "Growth Visibility", CommitmentLevel.HIGH, 90.0, Instant.now()
        );
        when(managementObservationReader.findRecentAcrossAllInstruments(7)).thenReturn(List.of(observation));

        GuidanceChangeDto dto = service.guidanceChanges(7).get(0);

        assertThat(dto.symbol()).isEqualTo("TCS");
        assertThat(dto.guidanceValue()).isEqualTo("30%");
        assertThat(dto.direction()).isEqualTo("POSITIVE");
        assertThat(dto.commitmentLevel()).isEqualTo("HIGH");
    }

    @Test
    void priceAdjustmentsComputesFactorForEachRecentAction() {
        UUID instrumentId = UUID.randomUUID();
        CorporateAction bonus = new CorporateAction(instrumentId, "TCS", "BONUS", LocalDate.of(2026, 6, 1), null, null, null, 1, 1, null, null);
        when(priceAdjustmentService.recentPriceAffectingActions(7)).thenReturn(List.of(bonus));
        when(priceAdjustmentEngine.factorFor(bonus)).thenReturn(new BigDecimal("0.500000"));

        PriceAdjustmentDto dto = service.priceAdjustments(7).get(0);

        assertThat(dto.symbol()).isEqualTo("TCS");
        assertThat(dto.actionType()).isEqualTo("BONUS");
        assertThat(dto.adjustmentFactor()).isEqualByComparingTo("0.5");
    }

    @Test
    void summaryAssemblesAllWidgetsWithDefaultWindows() {
        when(orderBookLedgerReader.findRecentAcrossAllInstruments(1)).thenReturn(List.of());
        when(corporateEventReader.findRecentAcrossAllInstruments(7)).thenReturn(List.of());
        when(managementObservationReader.findRecentAcrossAllInstruments(7)).thenReturn(List.of());
        when(newsLinkReader.findRecentByDirection(NewsImpactDirection.POSITIVE, 7)).thenReturn(List.of());
        when(newsLinkReader.findRecentByDirection(NewsImpactDirection.NEGATIVE, 7)).thenReturn(List.of());
        when(newsCatalystSnapshotReader.findAllLatest()).thenReturn(List.of());
        when(managementSnapshotReader.findAllLatest()).thenReturn(List.of());
        when(corporateScoreReader.findAllLatest()).thenReturn(List.of());
        when(priceAdjustmentService.recentPriceAffectingActions(7)).thenReturn(List.of());

        DashboardSummaryDto summary = service.summary();

        assertThat(summary.biggestOrders()).isEmpty();
        assertThat(summary.corporateScores()).isEmpty();
        assertThat(summary.priceAdjustments()).isEmpty();
        verify(orderBookLedgerReader).findRecentAcrossAllInstruments(1);
        verify(corporateEventReader).findRecentAcrossAllInstruments(7);
    }
}
