package com.alphagraph.corporate.news;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.corporate.api.NewsCatalystSnapshot;
import com.alphagraph.corporate.api.NewsCatalystTrend;
import com.alphagraph.corporate.api.NewsImpactDirection;
import com.alphagraph.corporate.api.NewsInstrumentLink;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Mirrors the seeded news-catalyst-* rules (common/V10) exactly, so thresholds tested here match production. */
class NewsCatalystEngineTest {

    private final NewsCatalystEngine engine = new NewsCatalystEngine();
    private final UUID instrumentId = UUID.randomUUID();
    private final LocalDate asOfDate = LocalDate.of(2026, 6, 1);

    private static final RuleSet RULES = new RuleSet(1, List.of(
        new Rule("news-catalyst-direction", "newsCatalystNetDirection", 1, List.of(
            new RuleCondition(RuleOperator.GTE, 2, null, 1.5),
            new RuleCondition(RuleOperator.LTE, -2, null, -1.5)
        )),
        new Rule("news-catalyst-volume", "newsCatalystVolume", 1, List.of(
            new RuleCondition(RuleOperator.GTE, 3, null, 1.0),
            new RuleCondition(RuleOperator.LTE, 1, null, -1.0)
        )),
        new Rule("news-catalyst-recency", "newsCatalystRecencyDays", 1, List.of(
            new RuleCondition(RuleOperator.LTE, 7, null, 0.5),
            new RuleCondition(RuleOperator.GTE, 30, null, -0.5)
        ))
    ));

    @Test
    void emptyLinkHistoryYieldsNeutralScoreAndNoneTrend() {
        NewsCatalystSnapshot snapshot = engine.calculate(input(List.of()), RULES);

        assertThat(snapshot.catalystScore()).isEqualTo(50.0);
        assertThat(snapshot.catalystTrend()).isEqualTo(NewsCatalystTrend.NONE);
        assertThat(snapshot.recentCatalystCount()).isZero();
    }

    @Test
    void excellentScoreWhenManyRecentPositiveLinks() {
        // Newest-first: 3 positive links, most recent within the last 7 days -> best case.
        List<NewsInstrumentLink> links = List.of(
            link(NewsImpactDirection.POSITIVE, daysBeforeAsOf(1)),
            link(NewsImpactDirection.POSITIVE, daysBeforeAsOf(3)),
            link(NewsImpactDirection.POSITIVE, daysBeforeAsOf(5))
        );

        NewsCatalystSnapshot snapshot = engine.calculate(input(links), RULES);

        assertThat(snapshot.catalystScore()).isEqualTo(80.0);
        assertThat(snapshot.catalystTrend()).isEqualTo(NewsCatalystTrend.POSITIVE);
    }

    @Test
    void poorScoreWhenManyRecentNegativeLinks() {
        List<NewsInstrumentLink> links = List.of(
            link(NewsImpactDirection.NEGATIVE, daysBeforeAsOf(1)),
            link(NewsImpactDirection.NEGATIVE, daysBeforeAsOf(2))
        );

        NewsCatalystSnapshot snapshot = engine.calculate(input(links), RULES);

        assertThat(snapshot.catalystTrend()).isEqualTo(NewsCatalystTrend.NEGATIVE);
        // Direction (-1.5) + volume<=1? no, 2 links so neither volume rule fires; recency<=7 fires (+0.5).
        assertThat(snapshot.catalystScore()).isEqualTo(50.0 + (-1.5 + 0.5) * 10.0);
    }

    @Test
    void mixedTrendWhenDirectionsCancelOut() {
        List<NewsInstrumentLink> links = List.of(
            link(NewsImpactDirection.POSITIVE, daysBeforeAsOf(1)),
            link(NewsImpactDirection.NEGATIVE, daysBeforeAsOf(2))
        );

        assertThat(engine.calculate(input(links), RULES).catalystTrend()).isEqualTo(NewsCatalystTrend.MIXED);
    }

    @Test
    void staleCatalystIsPenalizedByRecency() {
        List<NewsInstrumentLink> links = List.of(link(NewsImpactDirection.POSITIVE, daysBeforeAsOf(45)));

        NewsCatalystSnapshot snapshot = engine.calculate(input(links), RULES);

        // Single link: direction net=1 (no rule fires, needs >=2 or <=-2), volume=1 (LTE 1 -> -1.0), recency=45 (GTE 30 -> -0.5).
        assertThat(snapshot.catalystScore()).isEqualTo(50.0 + (-1.0 - 0.5) * 10.0);
    }

    @Test
    void confidenceGrowsWithLinkCountUpToFive() {
        List<NewsInstrumentLink> oneLink = List.of(link(NewsImpactDirection.POSITIVE, daysBeforeAsOf(1)));
        List<NewsInstrumentLink> fiveLinks = List.of(
            link(NewsImpactDirection.POSITIVE, daysBeforeAsOf(1)), link(NewsImpactDirection.POSITIVE, daysBeforeAsOf(2)),
            link(NewsImpactDirection.POSITIVE, daysBeforeAsOf(3)), link(NewsImpactDirection.POSITIVE, daysBeforeAsOf(4)),
            link(NewsImpactDirection.POSITIVE, daysBeforeAsOf(5))
        );

        assertThat(engine.calculate(input(oneLink), RULES).confidence()).isEqualTo(60.0);
        assertThat(engine.calculate(input(fiveLinks), RULES).confidence()).isEqualTo(100.0);
    }

    private NewsCatalystInput input(List<NewsInstrumentLink> links) {
        return new NewsCatalystInput(instrumentId, "TEST", links, asOfDate);
    }

    private NewsInstrumentLink link(NewsImpactDirection direction, Instant announcedAt) {
        return new NewsInstrumentLink(
            UUID.randomUUID(), UUID.randomUUID(), instrumentId, "TEST", direction, "signal", "summary", 90.0, announcedAt
        );
    }

    private Instant daysBeforeAsOf(int days) {
        return asOfDate.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().minus(days, ChronoUnit.DAYS);
    }
}
