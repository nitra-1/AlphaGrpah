package com.alphagraph.corporate.commentary;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.corporate.api.CommitmentLevel;
import com.alphagraph.corporate.api.GuidanceDirection;
import com.alphagraph.corporate.api.GuidanceTrend;
import com.alphagraph.corporate.api.ManagementCommentarySnapshot;
import com.alphagraph.corporate.api.ManagementCredibility;
import com.alphagraph.corporate.api.ManagementObservation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Mirrors the seeded management-* rules (common/V9) exactly, so thresholds tested here match production. */
class ManagementCommentaryEngineTest {

    private final ManagementCommentaryEngine engine = new ManagementCommentaryEngine();
    private final UUID instrumentId = UUID.randomUUID();
    private final LocalDate asOfDate = LocalDate.of(2026, 6, 1);

    private static final RuleSet RULES = new RuleSet(1, List.of(
        new Rule("management-guidance-direction", "guidanceDirectionSignal", 1, List.of(
            new RuleCondition(RuleOperator.GTE, 1, null, 1.5),
            new RuleCondition(RuleOperator.LTE, -1, null, -1.5)
        )),
        new Rule("management-guidance-persistence", "guidancePersistenceQuarters", 1, List.of(
            new RuleCondition(RuleOperator.GTE, 3, null, 1.0),
            new RuleCondition(RuleOperator.LTE, 0, null, -1.0)
        )),
        new Rule("management-commitment-strength", "commitmentStrengthSignal", 1, List.of(
            new RuleCondition(RuleOperator.GTE, 2, null, 0.5),
            new RuleCondition(RuleOperator.LTE, 0, null, -0.5)
        ))
    ));

    @Test
    void emptyObservationHistoryYieldsNeutralScoreAndUnknownTrend() {
        ManagementCommentarySnapshot snapshot = engine.calculate(input(List.of()), RULES);

        assertThat(snapshot.growthVisibilityScore()).isEqualTo(50.0);
        assertThat(snapshot.guidanceTrend()).isEqualTo(GuidanceTrend.UNKNOWN);
    }

    @Test
    void excellentScoreWhenPersistentPositiveHighCommitmentGuidance() {
        // Newest-first, 3 consecutive positive quarters at HIGH commitment.
        List<ManagementObservation> observations = List.of(
            observation("REVENUE_GUIDANCE", 30.0, GuidanceDirection.POSITIVE, CommitmentLevel.HIGH, daysAgo(0)),
            observation("REVENUE_GUIDANCE", 25.0, GuidanceDirection.POSITIVE, CommitmentLevel.HIGH, daysAgo(90)),
            observation("REVENUE_GUIDANCE", 22.0, GuidanceDirection.POSITIVE, CommitmentLevel.MEDIUM, daysAgo(180))
        );

        ManagementCommentarySnapshot snapshot = engine.calculate(input(observations), RULES);

        assertThat(snapshot.growthVisibilityScore()).isEqualTo(80.0);
        assertThat(snapshot.managementCredibility()).isEqualTo(ManagementCredibility.HIGH);
    }

    @Test
    void trendIsUpgradingWhenLatestNumericGuidanceExceedsPrevious() {
        List<ManagementObservation> observations = List.of(
            observation("REVENUE_GUIDANCE", 30.0, GuidanceDirection.POSITIVE, CommitmentLevel.HIGH, daysAgo(0)),
            observation("REVENUE_GUIDANCE", 25.0, GuidanceDirection.POSITIVE, CommitmentLevel.MEDIUM, daysAgo(90))
        );

        assertThat(engine.calculate(input(observations), RULES).guidanceTrend()).isEqualTo(GuidanceTrend.UPGRADING);
    }

    @Test
    void trendIsDowngradingWhenLatestNumericGuidanceIsLower() {
        List<ManagementObservation> observations = List.of(
            observation("REVENUE_GUIDANCE", 20.0, GuidanceDirection.POSITIVE, CommitmentLevel.MEDIUM, daysAgo(0)),
            observation("REVENUE_GUIDANCE", 25.0, GuidanceDirection.POSITIVE, CommitmentLevel.MEDIUM, daysAgo(90))
        );

        assertThat(engine.calculate(input(observations), RULES).guidanceTrend()).isEqualTo(GuidanceTrend.DOWNGRADING);
    }

    @Test
    void trendIsUnknownWithFewerThanTwoNumericObservations() {
        List<ManagementObservation> observations = List.of(
            observation("REVENUE_GUIDANCE", 30.0, GuidanceDirection.POSITIVE, CommitmentLevel.HIGH, daysAgo(0))
        );

        assertThat(engine.calculate(input(observations), RULES).guidanceTrend()).isEqualTo(GuidanceTrend.UNKNOWN);
    }

    @Test
    void nonRevenueObservationsDoNotAffectScoreOrTrend() {
        List<ManagementObservation> observations = List.of(
            observation("DEMAND", null, GuidanceDirection.POSITIVE, CommitmentLevel.HIGH, daysAgo(0)),
            observation("PRICING", null, GuidanceDirection.NEGATIVE, CommitmentLevel.LOW, daysAgo(10))
        );

        ManagementCommentarySnapshot snapshot = engine.calculate(input(observations), RULES);

        assertThat(snapshot.growthVisibilityScore()).isEqualTo(50.0);
        assertThat(snapshot.guidanceTrend()).isEqualTo(GuidanceTrend.UNKNOWN);
    }

    @Test
    void poorCredibilityWhenGuidanceStreakJustBroke() {
        List<ManagementObservation> observations = List.of(
            observation("REVENUE_GUIDANCE", 20.0, GuidanceDirection.NEGATIVE, CommitmentLevel.MEDIUM, daysAgo(0))
        );

        assertThat(engine.calculate(input(observations), RULES).managementCredibility()).isEqualTo(ManagementCredibility.LOW);
    }

    private ManagementCommentaryInput input(List<ManagementObservation> observations) {
        return new ManagementCommentaryInput(instrumentId, "TEST", observations, asOfDate);
    }

    private ManagementObservation observation(
        String metricType, Double numeric, GuidanceDirection direction, CommitmentLevel commitment, Instant observedAt
    ) {
        return new ManagementObservation(
            UUID.randomUUID(), UUID.randomUUID(), instrumentId, "TEST", metricType,
            numeric == null ? "qualitative" : numeric + "%", numeric, "FY27",
            direction, "signal", commitment, 90.0, observedAt
        );
    }

    private Instant daysAgo(int days) {
        return Instant.now().minusSeconds(days * 86400L);
    }
}
