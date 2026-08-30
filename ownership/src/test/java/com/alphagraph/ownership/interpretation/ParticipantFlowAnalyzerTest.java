package com.alphagraph.ownership.interpretation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class ParticipantFlowAnalyzerTest {

    private final ParticipantFlowAnalyzer analyzer = new ParticipantFlowAnalyzer();

    private static ParticipantDealActivity activity(UUID participantId, ParticipantType type, String buySell, String value, LocalDate date) {
        return new ParticipantDealActivity(participantId, "Participant " + participantId, type, 80.0, buySell, new BigDecimal(value), date);
    }

    @Test
    void sameParticipantBuyAndSellNearlyOffsettingIsVeryHighChurn() {
        UUID participant = UUID.randomUUID();
        List<ParticipantDealActivity> activities = List.of(
            activity(participant, ParticipantType.PROP_DESK, "BUY", "100000000", LocalDate.of(2026, 8, 26)),
            activity(participant, ParticipantType.PROP_DESK, "SELL", "98000000", LocalDate.of(2026, 8, 26))
        );

        SymbolFlowSummary summary = analyzer.analyze(activities);

        assertThat(summary.churnState()).isEqualTo(ChurnState.VERY_HIGH_CHURN);
        assertThat(summary.churnRatio()).isCloseTo(0.9899, offset(0.001));
    }

    @Test
    void aCleanOneSidedBuyStaysDirectional() {
        UUID participant = UUID.randomUUID();
        List<ParticipantDealActivity> activities = List.of(
            activity(participant, ParticipantType.MUTUAL_FUND, "BUY", "50000000", LocalDate.of(2026, 8, 26))
        );

        SymbolFlowSummary summary = analyzer.analyze(activities);

        assertThat(summary.churnState()).isEqualTo(ChurnState.DIRECTIONAL);
        assertThat(summary.churnRatio()).isEqualTo(0.0);
    }

    @Test
    void differentParticipantsOnEachSideDoesNotCountAsChurnEvenIfSymbolLevelBuyAndSellAreBalanced() {
        // Institutional buyers absorbing a seller's stock - a genuine ownership transition, not
        // round-trip churn, even though total buy roughly equals total sell across the symbol.
        UUID buyer1 = UUID.randomUUID();
        UUID buyer2 = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        List<ParticipantDealActivity> activities = List.of(
            activity(buyer1, ParticipantType.MUTUAL_FUND, "BUY", "50000000", LocalDate.of(2026, 8, 26)),
            activity(buyer2, ParticipantType.INSURANCE, "BUY", "50000000", LocalDate.of(2026, 8, 26)),
            activity(seller, ParticipantType.CORPORATE, "SELL", "100000000", LocalDate.of(2026, 8, 26))
        );

        SymbolFlowSummary summary = analyzer.analyze(activities);

        assertThat(summary.matchedRoundTripValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.churnState()).isEqualTo(ChurnState.DIRECTIONAL);
        assertThat(summary.institutionalBuyValue()).isEqualByComparingTo("100000000");
        assertThat(summary.institutionalBuyerCount()).isEqualTo(2);
    }

    @Test
    void propDominatedChurnHasHighPropShareAndConfidence() {
        UUID propParticipant = UUID.randomUUID();
        List<ParticipantDealActivity> activities = List.of(
            new ParticipantDealActivity(propParticipant, "XYZ Prop Desk", ParticipantType.PROP_DESK, 70.0, "BUY", new BigDecimal("100000000"), LocalDate.of(2026, 8, 26)),
            new ParticipantDealActivity(propParticipant, "XYZ Prop Desk", ParticipantType.PROP_DESK, 70.0, "SELL", new BigDecimal("100000000"), LocalDate.of(2026, 8, 26))
        );

        SymbolFlowSummary summary = analyzer.analyze(activities);

        assertThat(summary.propShareOfMatchedRoundTripValue()).isEqualTo(1.0);
        assertThat(summary.propWeightedConfidence()).isEqualTo(70.0);
    }

    @Test
    void repeatBehaviorClassifiesPersistentBuyerCorrectly() {
        UUID participant = UUID.randomUUID();
        List<ParticipantDealActivity> activities = List.of(
            activity(participant, ParticipantType.MUTUAL_FUND, "BUY", "10000000", LocalDate.of(2026, 8, 24)),
            activity(participant, ParticipantType.MUTUAL_FUND, "BUY", "10000000", LocalDate.of(2026, 8, 25)),
            activity(participant, ParticipantType.MUTUAL_FUND, "BUY", "10000000", LocalDate.of(2026, 8, 26))
        );

        SymbolFlowSummary summary = analyzer.analyze(activities);

        assertThat(summary.participantFlows()).hasSize(1);
        assertThat(summary.participantFlows().get(0).repeatBehavior()).isEqualTo(RepeatBehavior.PERSISTENT_BUYER);
        assertThat(summary.participantFlows().get(0).buySessions()).isEqualTo(3);
    }

    @Test
    void aSingleDealIsOneOff() {
        UUID participant = UUID.randomUUID();
        List<ParticipantDealActivity> activities = List.of(
            activity(participant, ParticipantType.MUTUAL_FUND, "BUY", "10000000", LocalDate.of(2026, 8, 26))
        );

        SymbolFlowSummary summary = analyzer.analyze(activities);

        assertThat(summary.participantFlows().get(0).repeatBehavior()).isEqualTo(RepeatBehavior.ONE_OFF);
    }
}
