package com.alphagraph.ownership.interpretation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryConfirmationEngineTest {

    private final DiscoveryConfirmationEngine engine = new DiscoveryConfirmationEngine();
    private static final LocalDate ANCHOR = LocalDate.of(2026, 8, 24);

    private static AnchorCandidateDeal buyDeal(BigDecimal price, BigDecimal quantity) {
        return new AnchorCandidateDeal(UUID.randomUUID(), ANCHOR, "BUY", MaterialityLevel.HIGH, quantity, price, price.multiply(quantity));
    }

    private static AnchorCandidateDeal sellDeal(BigDecimal price, BigDecimal quantity) {
        return new AnchorCandidateDeal(UUID.randomUUID(), ANCHOR, "SELL", MaterialityLevel.HIGH, quantity, price, price.multiply(quantity));
    }

    private static DiscoveredPriceRow priceRow(LocalDate date, BigDecimal close, long volume, BigDecimal deliveryPct) {
        return new DiscoveredPriceRow(date, close, close, close, close, volume, close.multiply(BigDecimal.valueOf(volume)), deliveryPct);
    }

    @Test
    void nonDirectionalStateIsAlwaysNotApplicable() {
        DiscoveryConfirmationResult result = engine.evaluate(
            InstitutionalState.HIGH_CHURN, ANCHOR, List.of(), List.of(), List.of(), List.of()
        );

        assertThat(result.state()).isEqualTo(DiscoveryConfirmationState.NOT_APPLICABLE);
        assertThat(result.confirmationScore()).isNull();
    }

    @Test
    void zeroPostAnchorSessionsIsAlwaysPendingRegardlessOfScore() {
        DiscoveryConfirmationResult result = engine.evaluate(
            InstitutionalState.POSSIBLE_ACCUMULATION, ANCHOR, List.of(buyDeal(new BigDecimal("100"), new BigDecimal("1000"))),
            List.of(), List.of(), List.of()
        );

        assertThat(result.state()).isEqualTo(DiscoveryConfirmationState.PENDING);
        assertThat(result.sessionsElapsed()).isEqualTo(0);
        assertThat(result.frozen()).isFalse();
    }

    @Test
    void priceComponentScoresAPositiveReturnAboveFiftyForAccumulation() {
        List<AnchorCandidateDeal> anchorDeals = List.of(buyDeal(new BigDecimal("100"), new BigDecimal("1000")));
        List<DiscoveredPriceRow> postAnchor = List.of(priceRow(ANCHOR.plusDays(1), new BigDecimal("105"), 1000, new BigDecimal("50")));

        DiscoveryConfirmationResult result = engine.evaluate(
            InstitutionalState.POSSIBLE_ACCUMULATION, ANCHOR, anchorDeals, postAnchor, List.of(), List.of()
        );

        // (105-100)/100 = 5% -> 50 + (5/5)*10 = 60
        assertThat(result.priceScore()).isEqualByComparingTo("60.00");
    }

    @Test
    void priceComponentIsSignedByDirectionForDistribution() {
        List<AnchorCandidateDeal> anchorDeals = List.of(sellDeal(new BigDecimal("100"), new BigDecimal("1000")));
        // Price fell 5% - that CONFIRMS distribution, so the score should be above 50, not below.
        List<DiscoveredPriceRow> postAnchor = List.of(priceRow(ANCHOR.plusDays(1), new BigDecimal("95"), 1000, new BigDecimal("50")));

        DiscoveryConfirmationResult result = engine.evaluate(
            InstitutionalState.POSSIBLE_DISTRIBUTION, ANCHOR, anchorDeals, postAnchor, List.of(), List.of()
        );

        assertThat(result.priceScore()).isEqualByComparingTo("60.00");
    }

    @Test
    void weightedEventPriceUsesOnlyTheConfirmingSideEvenWithSameDayOppositeSideDeals() {
        // Institutional BUY at 100 alongside unrelated same-day prop churn (BUY/SELL at 110-112) -
        // the accumulation price must not be dragged around by the churn leg.
        List<AnchorCandidateDeal> anchorDeals = List.of(
            buyDeal(new BigDecimal("100"), new BigDecimal("1000")),
            buyDeal(new BigDecimal("112"), new BigDecimal("500")),
            sellDeal(new BigDecimal("111"), new BigDecimal("500"))
        );
        // weighted BUY price = (100*1000 + 112*500) / 1500 = 137200/1500... let's use round numbers instead.
        List<DiscoveredPriceRow> postAnchor = List.of(priceRow(ANCHOR.plusDays(1), new BigDecimal("104"), 1000, new BigDecimal("50")));

        DiscoveryConfirmationResult result = engine.evaluate(
            InstitutionalState.POSSIBLE_ACCUMULATION, ANCHOR, anchorDeals, postAnchor, List.of(), List.of()
        );

        // weighted BUY-only price = (100*1000 + 112*500) / 1500 = 104.0 exactly -> return 0% -> score 50
        assertThat(result.priceScore()).isEqualByComparingTo("50.00");
    }

    @Test
    void deliveryComponentUsesPercentagePointsNotRelativePercentageChange() {
        List<DiscoveredPriceRow> preAnchor = List.of(priceRow(ANCHOR.minusDays(1), new BigDecimal("100"), 1000, new BigDecimal("40")));
        List<DiscoveredPriceRow> postAnchor = List.of(priceRow(ANCHOR.plusDays(1), new BigDecimal("100"), 1000, new BigDecimal("50")));

        DiscoveryConfirmationResult result = engine.evaluate(
            InstitutionalState.POSSIBLE_ACCUMULATION, ANCHOR, List.of(buyDeal(new BigDecimal("100"), new BigDecimal("1000"))),
            postAnchor, preAnchor, List.of()
        );

        // 40 -> 50 is +10 percentage points (not +25% relative) -> 50 + (10/10)*25 = 75
        assertThat(result.deliveryScore()).isEqualByComparingTo("75.00");
    }

    @Test
    void volumeComponentScoresOneHundredAtOnePointFiveRelativeVolume() {
        List<DiscoveredPriceRow> preAnchor = List.of(priceRow(ANCHOR.minusDays(1), new BigDecimal("100"), 1000, new BigDecimal("50")));
        List<DiscoveredPriceRow> postAnchor = List.of(priceRow(ANCHOR.plusDays(1), new BigDecimal("100"), 1500, new BigDecimal("50")));

        DiscoveryConfirmationResult result = engine.evaluate(
            InstitutionalState.POSSIBLE_ACCUMULATION, ANCHOR, List.of(buyDeal(new BigDecimal("100"), new BigDecimal("1000"))),
            postAnchor, preAnchor, List.of()
        );

        assertThat(result.volumeScore()).isEqualByComparingTo("100.00");
    }

    @Test
    void repeatActivityIsStrictlyPostAnchorNeverInflatedByPreAnchorRepetition() {
        // The direct regression test: a participant with strong PRE-anchor repeat buying but zero
        // real post-anchor activity must score 0 on repeat activity, not a misleadingly high score.
        List<DiscoveredPriceRow> postAnchor = List.of(priceRow(ANCHOR.plusDays(1), new BigDecimal("100"), 1000, new BigDecimal("50")));
        List<ParticipantDealActivity> emptyPostAnchorActivity = List.of();

        DiscoveryConfirmationResult result = engine.evaluate(
            InstitutionalState.POSSIBLE_ACCUMULATION, ANCHOR, List.of(buyDeal(new BigDecimal("100"), new BigDecimal("1000"))),
            postAnchor, List.of(), emptyPostAnchorActivity
        );

        assertThat(result.repeatScore()).isEqualByComparingTo("20.00");
    }

    @Test
    void repeatActivityCountsDistinctPostAnchorInstitutionalParticipantsOnTheConfirmingSide() {
        List<DiscoveredPriceRow> postAnchor = List.of(priceRow(ANCHOR.plusDays(1), new BigDecimal("100"), 1000, new BigDecimal("50")));
        List<ParticipantDealActivity> postAnchorActivity = List.of(
            new ParticipantDealActivity(UUID.randomUUID(), "MF One", ParticipantType.MUTUAL_FUND, 95, "BUY", new BigDecimal("1000"), ANCHOR.plusDays(1)),
            new ParticipantDealActivity(UUID.randomUUID(), "MF Two", ParticipantType.MUTUAL_FUND, 95, "BUY", new BigDecimal("1000"), ANCHOR.plusDays(1)),
            new ParticipantDealActivity(UUID.randomUUID(), "Some Prop", ParticipantType.PROP_DESK, 70, "BUY", new BigDecimal("1000"), ANCHOR.plusDays(1))
        );

        DiscoveryConfirmationResult result = engine.evaluate(
            InstitutionalState.POSSIBLE_ACCUMULATION, ANCHOR, List.of(buyDeal(new BigDecimal("100"), new BigDecimal("1000"))),
            postAnchor, List.of(), postAnchorActivity
        );

        assertThat(result.repeatScore()).isEqualByComparingTo("75.00");
    }

    @Test
    void freezesExactlyAtFiveSessionsNotBefore() {
        List<DiscoveredPriceRow> fourSessions = List.of(
            priceRow(ANCHOR.plusDays(1), new BigDecimal("100"), 1000, new BigDecimal("50")),
            priceRow(ANCHOR.plusDays(2), new BigDecimal("100"), 1000, new BigDecimal("50")),
            priceRow(ANCHOR.plusDays(3), new BigDecimal("100"), 1000, new BigDecimal("50")),
            priceRow(ANCHOR.plusDays(4), new BigDecimal("100"), 1000, new BigDecimal("50"))
        );
        DiscoveryConfirmationResult before = engine.evaluate(
            InstitutionalState.POSSIBLE_ACCUMULATION, ANCHOR, List.of(buyDeal(new BigDecimal("100"), new BigDecimal("1000"))),
            fourSessions, List.of(), List.of()
        );
        assertThat(before.frozen()).isFalse();

        List<DiscoveredPriceRow> fiveSessions = new java.util.ArrayList<>(fourSessions);
        fiveSessions.add(priceRow(ANCHOR.plusDays(5), new BigDecimal("100"), 1000, new BigDecimal("50")));
        DiscoveryConfirmationResult atFive = engine.evaluate(
            InstitutionalState.POSSIBLE_ACCUMULATION, ANCHOR, List.of(buyDeal(new BigDecimal("100"), new BigDecimal("1000"))),
            fiveSessions, List.of(), List.of()
        );
        assertThat(atFive.frozen()).isTrue();
        assertThat(atFive.sessionsElapsed()).isEqualTo(5);
    }

    @Test
    void sessionsBeyondFiveNeverChangeTheResultOrTheElapsedCount() {
        // Regression: frozen used to be a label only - a caller passing 10 sessions got "T+10 of 5"
        // and a score still drifting with sessions 6-10. Sessions 6-7 here have a wildly different
        // price/volume/delivery; the result must be identical to the exact-5-session result.
        List<DiscoveredPriceRow> fiveSessions = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            fiveSessions.add(priceRow(ANCHOR.plusDays(i), new BigDecimal("110"), 2000, new BigDecimal("60")));
        }
        List<DiscoveredPriceRow> sevenSessions = new java.util.ArrayList<>(fiveSessions);
        sevenSessions.add(priceRow(ANCHOR.plusDays(6), new BigDecimal("50"), 1, new BigDecimal("1")));
        sevenSessions.add(priceRow(ANCHOR.plusDays(7), new BigDecimal("50"), 1, new BigDecimal("1")));
        List<DiscoveredPriceRow> baseline = List.of(priceRow(ANCHOR.minusDays(1), new BigDecimal("100"), 1000, new BigDecimal("50")));
        List<AnchorCandidateDeal> deals = List.of(buyDeal(new BigDecimal("100"), new BigDecimal("1000")));

        DiscoveryConfirmationResult atFive = engine.evaluate(InstitutionalState.POSSIBLE_ACCUMULATION, ANCHOR, deals, fiveSessions, baseline, List.of());
        DiscoveryConfirmationResult withExtraSessions = engine.evaluate(InstitutionalState.POSSIBLE_ACCUMULATION, ANCHOR, deals, sevenSessions, baseline, List.of());

        assertThat(withExtraSessions.sessionsElapsed()).isEqualTo(5);
        assertThat(withExtraSessions.confirmationScore()).isEqualByComparingTo(atFive.confirmationScore());
        assertThat(withExtraSessions.priceScore()).isEqualByComparingTo(atFive.priceScore());
        assertThat(withExtraSessions.state()).isEqualTo(atFive.state());
    }

    @Test
    void institutionalBuyingAfterTheFifthSessionDoesNotCountTowardRepeatActivity() {
        List<DiscoveredPriceRow> sevenSessions = new java.util.ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            sevenSessions.add(priceRow(ANCHOR.plusDays(i), new BigDecimal("100"), 1000, new BigDecimal("50")));
        }
        List<ParticipantDealActivity> activity = List.of(
            new ParticipantDealActivity(UUID.randomUUID(), "MF One", ParticipantType.MUTUAL_FUND, 95, "BUY", new BigDecimal("1000"), ANCHOR.plusDays(6)),
            new ParticipantDealActivity(UUID.randomUUID(), "MF Two", ParticipantType.MUTUAL_FUND, 95, "BUY", new BigDecimal("1000"), ANCHOR.plusDays(7))
        );

        DiscoveryConfirmationResult result = engine.evaluate(
            InstitutionalState.POSSIBLE_ACCUMULATION, ANCHOR, List.of(buyDeal(new BigDecimal("100"), new BigDecimal("1000"))),
            sevenSessions, List.of(), activity
        );

        assertThat(result.repeatScore()).isEqualByComparingTo("20.00");
    }
}
