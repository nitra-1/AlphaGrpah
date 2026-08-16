package com.alphagraph.decision.report;

import com.alphagraph.corporate.api.CommitmentLevel;
import com.alphagraph.corporate.api.CorporateEvent;
import com.alphagraph.corporate.api.EventSignal;
import com.alphagraph.corporate.api.EventType;
import com.alphagraph.corporate.api.GuidanceDirection;
import com.alphagraph.corporate.api.ManagementObservation;
import com.alphagraph.corporate.api.NewsImpactDirection;
import com.alphagraph.corporate.api.NewsInstrumentLink;
import com.alphagraph.corporate.api.RevenueImpact;
import com.alphagraph.corporate.commentary.ManagementObservationReader;
import com.alphagraph.corporate.events.CorporateEventReader;
import com.alphagraph.corporate.news.NewsLinkReader;
import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.api.PortfolioHolding;
import com.alphagraph.decision.api.WatchlistItem;
import com.alphagraph.decision.engine.DecisionScoreReader;
import com.alphagraph.decision.portfolio.PortfolioService;
import com.alphagraph.decision.watchlist.WatchlistService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyReportEvidenceBuilderTest {

    private final DecisionScoreReader decisionScoreReader = mock(DecisionScoreReader.class);
    private final WatchlistService watchlistService = mock(WatchlistService.class);
    private final PortfolioService portfolioService = mock(PortfolioService.class);
    private final CorporateEventReader corporateEventReader = mock(CorporateEventReader.class);
    private final ManagementObservationReader managementObservationReader = mock(ManagementObservationReader.class);
    private final NewsLinkReader newsLinkReader = mock(NewsLinkReader.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DailyReportEvidenceBuilder builder = new DailyReportEvidenceBuilder(
        decisionScoreReader, watchlistService, portfolioService, corporateEventReader, managementObservationReader, newsLinkReader, jdbcTemplate
    );

    private final UUID adminUserId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 6, 2);
    private final LocalDate yesterday = LocalDate.of(2026, 6, 1);

    private void stubEmptyCorporateFacts() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(UUID.class), eq("admin@alphagraph.local"))).thenReturn(adminUserId);
        when(corporateEventReader.findRecentAcrossAllInstruments(1)).thenReturn(List.of());
        when(managementObservationReader.findRecentAcrossAllInstruments(1)).thenReturn(List.of());
        when(newsLinkReader.findRecentByDirection(NewsImpactDirection.POSITIVE, 1)).thenReturn(List.of());
        when(newsLinkReader.findRecentByDirection(NewsImpactDirection.NEGATIVE, 1)).thenReturn(List.of());
        when(watchlistService.list(adminUserId)).thenReturn(List.of());
        when(portfolioService.list(adminUserId)).thenReturn(List.of());
    }

    @Test
    void identifiesTheBiggestRankImproverAndDeclinerAcrossTheWholeCohort() {
        UUID tcs = UUID.randomUUID();
        UUID xyz = UUID.randomUUID();
        when(decisionScoreReader.findAllByDate(today)).thenReturn(List.of(score(tcs, "TCS", 1), score(xyz, "XYZ", 5)));
        when(decisionScoreReader.findAllByDate(yesterday)).thenReturn(List.of(score(tcs, "TCS", 4), score(xyz, "XYZ", 2)));
        stubEmptyCorporateFacts();

        DailyReportEvidence evidence = builder.build(today);

        assertThat(evidence.topGainerSymbol()).isEqualTo("TCS");
        assertThat(evidence.topGainerRankImprovement()).isEqualTo(3);
        assertThat(evidence.topDeclinerSymbol()).isEqualTo("XYZ");
        assertThat(evidence.topDeclinerRankDecline()).isEqualTo(3);
        assertThat(evidence.facts()).anyMatch(f -> f.factType().equals("RANK_IMPROVEMENT") && f.description().contains("TCS moved from Rank 4 to Rank 1"));
        assertThat(evidence.facts()).anyMatch(f -> f.factType().equals("RANK_DECLINE") && f.description().contains("XYZ moved from Rank 2 to Rank 5"));
    }

    @Test
    void noPriorDayDataMeansNoRankFactsRatherThanAFabricatedComparison() {
        UUID tcs = UUID.randomUUID();
        when(decisionScoreReader.findAllByDate(today)).thenReturn(List.of(score(tcs, "TCS", 1)));
        when(decisionScoreReader.findAllByDate(yesterday)).thenReturn(List.of());
        stubEmptyCorporateFacts();

        DailyReportEvidence evidence = builder.build(today);

        assertThat(evidence.topGainerSymbol()).isNull();
        assertThat(evidence.topDeclinerSymbol()).isNull();
        assertThat(evidence.facts()).noneMatch(f -> f.factType().equals("RANK_IMPROVEMENT") || f.factType().equals("RANK_DECLINE"));
    }

    @Test
    void corporateEventsGuidanceAndNewsAreConvertedToFactsWithRealCounts() {
        when(decisionScoreReader.findAllByDate(today)).thenReturn(List.of());
        when(decisionScoreReader.findAllByDate(yesterday)).thenReturn(List.of());
        when(corporateEventReader.findRecentAcrossAllInstruments(1)).thenReturn(List.of(
            new CorporateEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BEL",
                EventType.LARGE_ORDER, "Order", "Won a large order", 90.0, null, RevenueImpact.HIGH, EventSignal.POSITIVE, 1, Instant.now())
        ));
        when(managementObservationReader.findRecentAcrossAllInstruments(1)).thenReturn(List.of(
            new ManagementObservation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "TCS", "REVENUE_GUIDANCE",
                "30%", 30.0, "FY27", GuidanceDirection.POSITIVE, "Growth", CommitmentLevel.HIGH, 90.0, Instant.now())
        ));
        when(newsLinkReader.findRecentByDirection(NewsImpactDirection.POSITIVE, 1)).thenReturn(List.of(
            new NewsInstrumentLink(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "INFY", NewsImpactDirection.POSITIVE,
                "Signal", "Won a marquee client", 85.0, Instant.now())
        ));
        when(newsLinkReader.findRecentByDirection(NewsImpactDirection.NEGATIVE, 1)).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(any(String.class), eq(UUID.class), eq("admin@alphagraph.local"))).thenReturn(adminUserId);
        when(watchlistService.list(adminUserId)).thenReturn(List.of());
        when(portfolioService.list(adminUserId)).thenReturn(List.of());

        DailyReportEvidence evidence = builder.build(today);

        assertThat(evidence.newEventCount()).isEqualTo(1);
        assertThat(evidence.guidanceChangeCount()).isEqualTo(1);
        assertThat(evidence.positiveNewsCount()).isEqualTo(1);
        assertThat(evidence.negativeNewsCount()).isEqualTo(0);
        assertThat(evidence.facts()).anyMatch(f -> f.factType().equals("CORPORATE_EVENT") && f.description().contains("BEL"));
        assertThat(evidence.facts()).anyMatch(f -> f.factType().equals("GUIDANCE_CHANGE") && f.description().contains("TCS"));
        assertThat(evidence.facts()).anyMatch(f -> f.factType().equals("POSITIVE_NEWS") && f.description().contains("INFY"));
    }

    @Test
    void watchlistHighlightsOnlyIncludeInstrumentsThatActuallyMovedAndAreCappedAtThree() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        UUID unchanged = UUID.randomUUID();
        // Deltas: a=4, b=3, c=2, d=1 (all real movers, four of them - more than the cap of 3),
        // unchanged=0 (must be excluded regardless of the cap).
        when(decisionScoreReader.findAllByDate(today)).thenReturn(List.of(
            score(a, "A", 1), score(b, "B", 2), score(c, "C", 3), score(d, "D", 4), score(unchanged, "E", 5)
        ));
        when(decisionScoreReader.findAllByDate(yesterday)).thenReturn(List.of(
            score(a, "A", 5), score(b, "B", 5), score(c, "C", 5), score(d, "D", 5), score(unchanged, "E", 5)
        ));
        stubCorporateFactsOnly();
        when(watchlistService.list(adminUserId)).thenReturn(List.of(
            watchlistItem(a), watchlistItem(b), watchlistItem(c), watchlistItem(d), watchlistItem(unchanged)
        ));
        when(portfolioService.list(adminUserId)).thenReturn(List.of());

        DailyReportEvidence evidence = builder.build(today);

        List<ReportFact> highlights = evidence.facts().stream().filter(f -> f.factType().equals("WATCHLIST_MOVER")).toList();
        assertThat(highlights).hasSize(3); // capped at 3 of the 4 real movers; "unchanged" was never a candidate
        assertThat(highlights.get(0).description()).contains("A"); // biggest mover (delta 4) first
        assertThat(highlights).noneMatch(f -> f.description().contains(" D ") || f.description().contains(": D "));
    }

    @Test
    void portfolioHighlightsAreIndependentOfWatchlistHighlights() {
        UUID heldInstrument = UUID.randomUUID();
        when(decisionScoreReader.findAllByDate(today)).thenReturn(List.of(score(heldInstrument, "TCS", 1)));
        when(decisionScoreReader.findAllByDate(yesterday)).thenReturn(List.of(score(heldInstrument, "TCS", 3)));
        stubCorporateFactsOnly();
        when(watchlistService.list(adminUserId)).thenReturn(List.of());
        when(portfolioService.list(adminUserId)).thenReturn(List.of(holding(heldInstrument)));

        DailyReportEvidence evidence = builder.build(today);

        assertThat(evidence.facts()).anyMatch(f -> f.factType().equals("PORTFOLIO_MOVER") && f.description().contains("TCS"));
        assertThat(evidence.facts()).noneMatch(f -> f.factType().equals("WATCHLIST_MOVER"));
    }

    private void stubCorporateFactsOnly() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(UUID.class), eq("admin@alphagraph.local"))).thenReturn(adminUserId);
        when(corporateEventReader.findRecentAcrossAllInstruments(1)).thenReturn(List.of());
        when(managementObservationReader.findRecentAcrossAllInstruments(1)).thenReturn(List.of());
        when(newsLinkReader.findRecentByDirection(NewsImpactDirection.POSITIVE, 1)).thenReturn(List.of());
        when(newsLinkReader.findRecentByDirection(NewsImpactDirection.NEGATIVE, 1)).thenReturn(List.of());
    }

    private DecisionScore score(UUID instrumentId, String symbol, int swingRank) {
        return new DecisionScore(
            instrumentId, symbol, today,
            80.0, DecisionRating.BUY, swingRank,
            70.0, DecisionRating.BUY, swingRank,
            80.0, 80.0, 80.0, 80.0, 80.0, 80.0,
            100.0, 1, Instant.now(),
            null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );
    }

    private WatchlistItem watchlistItem(UUID instrumentId) {
        return new WatchlistItem(UUID.randomUUID(), instrumentId, "SYM", Instant.now());
    }

    private PortfolioHolding holding(UUID instrumentId) {
        return new PortfolioHolding(UUID.randomUUID(), instrumentId, "TCS", BigDecimal.TEN, BigDecimal.TEN, Instant.now(), Instant.now());
    }
}
