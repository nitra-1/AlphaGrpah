package com.alphagraph.decision.engine;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.decision.api.DecisionScore;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Mirrors the seeded decision-swing-/decision-longterm- rules (common/V13) exactly, so weights tested here match production. */
class DecisionScoringEngineTest {

    private final DecisionScoringEngine engine = new DecisionScoringEngine();
    private final UUID instrumentId = UUID.randomUUID();
    private final LocalDate asOfDate = LocalDate.of(2026, 6, 1);

    private static final RuleSet RULES = new RuleSet(1, List.of(
        alwaysRule("decision-swing-technical", "decisionTechnicalScore", 0.35),
        alwaysRule("decision-swing-fundamental", "decisionFundamentalScore", 0.05),
        alwaysRule("decision-swing-institutional", "decisionInstitutionalScore", 0.15),
        alwaysRule("decision-swing-sector", "decisionSectorScore", 0.15),
        alwaysRule("decision-swing-risk", "decisionRiskScore", 0.10),
        alwaysRule("decision-swing-corporate", "decisionCorporateScore", 0.20),
        alwaysRule("decision-longterm-technical", "decisionTechnicalScore", 0.05),
        alwaysRule("decision-longterm-fundamental", "decisionFundamentalScore", 0.35),
        alwaysRule("decision-longterm-institutional", "decisionInstitutionalScore", 0.20),
        alwaysRule("decision-longterm-sector", "decisionSectorScore", 0.05),
        alwaysRule("decision-longterm-risk", "decisionRiskScore", 0.20),
        alwaysRule("decision-longterm-corporate", "decisionCorporateScore", 0.15)
    ));

    private static Rule alwaysRule(String name, String targetMetric, double weight) {
        return new Rule(name, targetMetric, 1, List.of(new RuleCondition(RuleOperator.ALWAYS, 0.0, weight)));
    }

    @Test
    void allSixDomainsAtTheSameValueYieldsThatSameValueRegardlessOfWeights() {
        DecisionScoringInput input = new DecisionScoringInput(
            instrumentId, "TEST", 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, asOfDate
        );

        DecisionScore score = engine.calculate(input, RULES);

        assertThat(score.swingScore()).isEqualTo(80.0);
        assertThat(score.longTermScore()).isEqualTo(80.0);
        assertThat(score.swingRating()).isEqualTo(DecisionRating.STRONG_BUY);
        assertThat(score.longTermRating()).isEqualTo(DecisionRating.STRONG_BUY);
        assertThat(score.confidence()).isEqualTo(100.0);
    }

    @Test
    void fullyCoveredInstrumentComputesAGenuineWeightedAverageNotJustAnEqualSplit() {
        // technical=100, everything else=0 (but present): swing = 0.35*100 = 35, longTerm = 0.05*100 = 5.
        DecisionScoringInput input = new DecisionScoringInput(
            instrumentId, "TEST", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, asOfDate
        );

        DecisionScore score = engine.calculate(input, RULES);

        assertThat(score.swingScore()).isEqualTo(35.0);
        assertThat(score.longTermScore()).isEqualTo(5.0);
        assertThat(score.swingRating()).isEqualTo(DecisionRating.REDUCE);
        assertThat(score.longTermRating()).isEqualTo(DecisionRating.AVOID);
        assertThat(score.confidence()).isEqualTo(100.0); // all 6 present, even though most are 0.0
    }

    @Test
    void singleDomainPresentRenormalizesToThatDomainsOwnValueRatherThanBeingDiluted() {
        // Only fundamental present at 60 - naively (no renormalization) swing would compute
        // 0.05*60=3.0 and stop there. Renormalized over the one present domain, it must be 60.0.
        DecisionScoringInput input = new DecisionScoringInput(
            instrumentId, "TEST", null, 60.0, null, null, null, null, asOfDate
        );

        DecisionScore score = engine.calculate(input, RULES);

        assertThat(score.swingScore()).isEqualTo(60.0);
        assertThat(score.longTermScore()).isEqualTo(60.0);
        assertThat(score.confidence()).isEqualTo(25.0);
    }

    @Test
    void missingDomainsAreNullInTheOutputAndExcludedFromTheAverage() {
        DecisionScoringInput input = new DecisionScoringInput(
            instrumentId, "TEST", 90.0, null, 70.0, null, null, null, asOfDate
        );

        DecisionScore score = engine.calculate(input, RULES);

        assertThat(score.technicalScore()).isEqualTo(90.0);
        assertThat(score.fundamentalScore()).isNull();
        assertThat(score.institutionalScore()).isEqualTo(70.0);
        assertThat(score.sectorScore()).isNull();
        assertThat(score.confidence()).isEqualTo(40.0); // 10 + 15*2
        // swing: (0.35*90 + 0.15*70) / (0.35 + 0.15) = (31.5 + 10.5) / 0.5 = 84.0
        assertThat(score.swingScore()).isEqualTo(84.0);
    }

    @Test
    void ratingBandBoundariesAreInclusiveAtTheLowerEdgeViaSingleDomainRenormalization() {
        assertThat(swingRatingForSoleDomain(80.0)).isEqualTo(DecisionRating.STRONG_BUY);
        assertThat(swingRatingForSoleDomain(79.99)).isEqualTo(DecisionRating.BUY);
        assertThat(swingRatingForSoleDomain(65.0)).isEqualTo(DecisionRating.BUY);
        assertThat(swingRatingForSoleDomain(64.99)).isEqualTo(DecisionRating.HOLD);
        assertThat(swingRatingForSoleDomain(40.0)).isEqualTo(DecisionRating.HOLD);
        assertThat(swingRatingForSoleDomain(39.99)).isEqualTo(DecisionRating.REDUCE);
        assertThat(swingRatingForSoleDomain(25.0)).isEqualTo(DecisionRating.REDUCE);
        assertThat(swingRatingForSoleDomain(24.99)).isEqualTo(DecisionRating.AVOID);
    }

    private DecisionRating swingRatingForSoleDomain(double technicalScore) {
        DecisionScoringInput input = new DecisionScoringInput(
            instrumentId, "TEST", technicalScore, null, null, null, null, null, asOfDate
        );
        return engine.calculate(input, RULES).swingRating();
    }

    @Test
    void ranksAreAlwaysNullFromTheEngineItself() {
        DecisionScoringInput input = new DecisionScoringInput(
            instrumentId, "TEST", 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, asOfDate
        );

        DecisionScore score = engine.calculate(input, RULES);

        assertThat(score.swingRank()).isNull();
        assertThat(score.longTermRank()).isNull();
    }

    @Test
    void scoreCarriesTheInstrumentSymbolDateAndRuleSetVersion() {
        DecisionScoringInput input = new DecisionScoringInput(
            instrumentId, "TEST", 80.0, null, null, null, null, null, asOfDate
        );

        DecisionScore score = engine.calculate(input, RULES);

        assertThat(score.instrumentId()).isEqualTo(instrumentId);
        assertThat(score.symbol()).isEqualTo("TEST");
        assertThat(score.asOfDate()).isEqualTo(asOfDate);
        assertThat(score.ruleSetVersion()).isEqualTo(1);
        assertThat(score.value()).isEqualTo(score.swingScore());
    }
}
