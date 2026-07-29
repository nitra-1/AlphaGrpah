package com.alphagraph.risk.engine;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.risk.api.RiskEngineInput;
import com.alphagraph.risk.api.RiskLevel;
import com.alphagraph.risk.api.RiskScore;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RiskEngineTest {

    private final RiskEngine engine = new RiskEngine();

    // Mirrors the 13 rules seeded by common's V7 migration (Module 1.9). Weights are chosen so
    // each category's own best-case raw sum is exactly 3.0 (score 80, VERY_LOW) and its
    // worst-case sum is at or beyond -3.0 (score <=20, VERY_HIGH), regardless of how many
    // signals feed that category - see V7's own comment for the full rationale.
    private static RuleSet defaultRuleSet() {
        List<Rule> rules = List.of(
            new Rule("risk-business-revenue-growth", "revenueGrowthPct", 1, List.of(
                new RuleCondition(RuleOperator.GT, 10, 1.0),
                new RuleCondition(RuleOperator.LT, 0, -1.0))),
            new Rule("risk-business-profitability-trend", "profitabilityTrend", 1, List.of(
                new RuleCondition(RuleOperator.GTE, 1, 1.0),
                new RuleCondition(RuleOperator.LTE, -1, -1.0))),
            new Rule("risk-business-leverage", "debtToEquity", 1, List.of(
                new RuleCondition(RuleOperator.LT, 0.5, 0.5),
                new RuleCondition(RuleOperator.GT, 1.5, -1.0))),
            new Rule("risk-business-cash-conversion", "cashConversionRatio", 1, List.of(
                new RuleCondition(RuleOperator.GT, 1.0, 0.5),
                new RuleCondition(RuleOperator.LT, 0, -1.0))),
            new Rule("risk-technical-trend", "technicalTrendSignal", 1, List.of(
                new RuleCondition(RuleOperator.GTE, 1, 1.5),
                new RuleCondition(RuleOperator.LTE, -1, -1.5))),
            new Rule("risk-technical-momentum", "momentumSignal", 1, List.of(
                new RuleCondition(RuleOperator.GTE, 1, 1.0),
                new RuleCondition(RuleOperator.LTE, -1, -1.0))),
            new Rule("risk-technical-volume", "volumeSignal", 1, List.of(
                new RuleCondition(RuleOperator.GTE, 1, 0.5),
                new RuleCondition(RuleOperator.LTE, -1, -1.0))),
            new Rule("risk-ownership-promoter", "promoterTrendSignal", 1, List.of(
                new RuleCondition(RuleOperator.GTE, 1, 1.5),
                new RuleCondition(RuleOperator.LTE, -1, -1.5))),
            new Rule("risk-ownership-fii", "fiiTrendSignal", 1, List.of(
                new RuleCondition(RuleOperator.GTE, 1, 0.5),
                new RuleCondition(RuleOperator.LTE, -1, -1.0))),
            new Rule("risk-ownership-mf", "mfTrendSignal", 1, List.of(
                new RuleCondition(RuleOperator.GTE, 1, 0.5),
                new RuleCondition(RuleOperator.LTE, -1, -1.0))),
            new Rule("risk-ownership-delivery", "deliverySignal", 1, List.of(
                new RuleCondition(RuleOperator.GTE, 1, 0.5),
                new RuleCondition(RuleOperator.LTE, -1, -1.0))),
            new Rule("risk-valuation-pe", "peRatio", 1, List.of(
                new RuleCondition(RuleOperator.LT, 15, 1.5),
                new RuleCondition(RuleOperator.GT, 30, -1.5))),
            new Rule("risk-valuation-pb", "pbRatio", 1, List.of(
                new RuleCondition(RuleOperator.LT, 2, 1.5),
                new RuleCondition(RuleOperator.GT, 5, -1.5)))
        );
        return new RuleSet(1, rules);
    }

    private static RiskEngineInput baseInput(
        String trend, String momentum, String volumeState,
        String businessGrowth, String profitability, String financialQuality,
        Double debtToEquity, Double revenueGrowthPct, Double cashConversionRatio,
        String promoterStatus, String fiiStatus, String mfStatus, String deliveryStatus,
        Double latestClose, Double eps, Double pat, Double totalEquity
    ) {
        return new RiskEngineInput(
            UUID.randomUUID(), "TEST", LocalDate.of(2026, 7, 28),
            trend, momentum, volumeState,
            businessGrowth, profitability, financialQuality, debtToEquity, revenueGrowthPct, cashConversionRatio,
            promoterStatus, fiiStatus, mfStatus, deliveryStatus,
            latestClose, eps, pat, totalEquity
        );
    }

    @Test
    void safeInstrumentWithBestCaseSignalsInEveryCategoryReachesVeryLowOverallRisk() {
        // Every signal set to its single best-matching value, so each category hits its own
        // max raw sum of +3.0 -> score 80 -> VERY_LOW for all four categories and overall.
        // PE: close=100, eps=10 -> 10 (<15). PB: pat=50, totalEquity=400 -> bookValuePerShare =
        // 400*10/50 = 80 -> PB = 100/80 = 1.25 (<2).
        RiskEngineInput input = baseInput(
            "STRONG_UPTREND", "IMPROVING", "STRONG",
            "EXCELLENT", "IMPROVING", "STRONG", 0.2, 25.0, 1.5,
            "ACCUMULATING", "BUYING", "BUYING", "VERY_HIGH",
            100.0, 10.0, 50.0, 400.0
        );

        RiskScore score = engine.calculate(input, defaultRuleSet());

        assertThat(score.businessRisk()).isEqualTo(RiskLevel.VERY_LOW);
        assertThat(score.technicalRisk()).isEqualTo(RiskLevel.VERY_LOW);
        assertThat(score.ownershipRisk()).isEqualTo(RiskLevel.VERY_LOW);
        assertThat(score.valuationRisk()).isEqualTo(RiskLevel.VERY_LOW);
        assertThat(score.overallRisk()).isEqualTo(RiskLevel.VERY_LOW);
        assertThat(score.riskScore()).isEqualTo(80.0);
        assertThat(score.peRatio()).isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(score.pbRatio()).isCloseTo(1.25, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void riskyInstrumentWithWorstCaseSignalsInEveryCategoryReachesVeryHighOverallRisk() {
        // Every signal set to its single worst-matching value. Business raw = -4.0 (score 10),
        // Technical raw = -3.5 (score 15), Ownership raw = -4.5 (score 5), Valuation raw = -3.0
        // (score 20) - all four land in VERY_HIGH. PE: close=500, eps=5 -> 100 (>30). PB: pat=10,
        // totalEquity=100 -> bookValuePerShare = 100*5/10 = 50 -> PB = 500/50 = 10 (>5).
        RiskEngineInput input = baseInput(
            "STRONG_DOWNTREND", "WEAKENING", "WEAK",
            "DECLINING", "DECLINING", "WEAK", 2.5, -15.0, -0.5,
            "DISTRIBUTING", "SELLING", "SELLING", "LOW",
            500.0, 5.0, 10.0, 100.0
        );

        RiskScore score = engine.calculate(input, defaultRuleSet());

        assertThat(score.businessRisk()).isEqualTo(RiskLevel.VERY_HIGH);
        assertThat(score.technicalRisk()).isEqualTo(RiskLevel.VERY_HIGH);
        assertThat(score.ownershipRisk()).isEqualTo(RiskLevel.VERY_HIGH);
        assertThat(score.valuationRisk()).isEqualTo(RiskLevel.VERY_HIGH);
        assertThat(score.overallRisk()).isEqualTo(RiskLevel.VERY_HIGH);
        assertThat(score.riskScore()).isCloseTo(12.5, org.assertj.core.data.Offset.offset(0.01));
        assertThat(score.peRatio()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(score.pbRatio()).isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void mixedSignalsAcrossCategoriesProduceDistinctPerCategoryBandsNotAUniformScore() {
        // Strong technical picture (uptrend/improving/strong volume) alongside flat business
        // metrics, institutions selling, and an expensive valuation - the roadmap's worked
        // example (Business=Low, Ownership=Medium, Technical=Very Low, Valuation=High,
        // Overall=Medium) is a real instance of this same shape: categories move independently,
        // not in lockstep.
        // Technical raw = 1.5+1.0+0.5 = 3.0 -> score 80 (VERY_LOW).
        // Ownership raw = 0 (promoter stable) -1.0 (fii selling) -1.0 (mf selling) +0 (delivery
        // moderate) = -2.0 -> score 30 (HIGH).
        // Business raw = 0 (profitability stable, within thresholds) -> score 50 (MEDIUM).
        // Valuation: PE = 200/4 = 50 (>30, -1.5); PB: bookValuePerShare = 200*4/20 = 40, PB =
        // 200/40 = 5 (not >5, no match) -> raw = -1.5 -> score 35 (HIGH).
        // Overall = (50 + 80 + 30 + 35) / 4 = 48.75 -> MEDIUM, matching the roadmap's example.
        RiskEngineInput input = baseInput(
            "STRONG_UPTREND", "IMPROVING", "STRONG",
            "GOOD", "STABLE", "ADEQUATE", 0.6, 8.0, null,
            "STABLE", "SELLING", "SELLING", "MODERATE",
            200.0, 4.0, 20.0, 200.0
        );

        RiskScore score = engine.calculate(input, defaultRuleSet());

        assertThat(score.technicalRisk()).isEqualTo(RiskLevel.VERY_LOW);
        assertThat(score.ownershipRisk()).isEqualTo(RiskLevel.HIGH);
        assertThat(score.businessRisk()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(score.valuationRisk()).isEqualTo(RiskLevel.HIGH);
        assertThat(score.overallRisk()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(score.riskScore()).isCloseTo(48.75, org.assertj.core.data.Offset.offset(0.01));
    }
}
