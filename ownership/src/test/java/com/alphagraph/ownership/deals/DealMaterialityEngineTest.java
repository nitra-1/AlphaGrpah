package com.alphagraph.ownership.deals;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link DealMaterialityEngine} against an in-memory {@link RuleSet} that mirrors
 * {@code common/.../V14__seed_deal_materiality_engine_rules.sql} exactly, so these tests don't
 * depend on a live database and stay in lockstep with the real seeded rules by construction.
 */
class DealMaterialityEngineTest {

    private final DealMaterialityEngine engine = new DealMaterialityEngine();
    private final RuleSet rules = buildRuleSet();

    private static DealMaterialityInput baseInput(
        BigDecimal dealValue, BigDecimal adtv20, String direction,
        int sameSideCount, int distinctSameSide, int distinctBuyers, int distinctSellers,
        BigDecimal reportedBuyValue, BigDecimal reportedSellValue
    ) {
        return new DealMaterialityInput(
            UUID.randomUUID(), "AASTHA", LocalDate.of(2026, 7, 24), dealValue, direction, adtv20,
            sameSideCount, distinctSameSide, distinctBuyers, distinctSellers, reportedBuyValue, reportedSellValue
        );
    }

    @Test
    void sameInputAlwaysProducesTheSameScore() {
        DealMaterialityInput input = baseInput(
            new BigDecimal("50000000.00"), new BigDecimal("10000000.00"), "BUY",
            2, 2, 2, 0, new BigDecimal("500000"), new BigDecimal("500000")
        );

        DealMaterialityResult first = engine.calculate(input, rules);
        DealMaterialityResult second = engine.calculate(input, rules);

        assertThat(first.materialityScore()).isEqualTo(second.materialityScore());
        assertThat(first.materialityLevel()).isEqualTo(second.materialityLevel());
    }

    @Test
    void exactlyOneTimesAdtvRatioReachesTheVeryHighAdtvBand() {
        // Deal value == ADTV exactly -> ratio == 1.00 -> the VERY_HIGH (weight 100) band per
        // V14's cumulative GTE-1.00 step. Repetition/breadth held at their weakest (1, 1) so the
        // blended score reflects the ratio band alone, not inflated by the other two inputs.
        DealMaterialityInput input = baseInput(
            new BigDecimal("100000000.00"), new BigDecimal("100000000.00"), "BUY",
            1, 1, 1, 0, new BigDecimal("500000"), new BigDecimal("500000")
        );

        DealMaterialityResult result = engine.calculate(input, rules);

        assertThat(result.dealToAdtvRatio()).isEqualByComparingTo("1.0000");
        // adtv=100 (weight 0.70), repetition=20 (weight 0.20), breadth=20 (weight 0.10):
        // 100*0.70 + 20*0.20 + 20*0.10 = 76.00
        assertThat(result.materialityScore()).isEqualTo(76.00);
    }

    @Test
    void aPureSellDealWithHighRepetitionAndBreadthReachesHighMaterialityJustLikeABuyWould() {
        // The direct regression test for the BUY-bias fix: no buy activity at all in this
        // window, yet a SELL deal repeated 4+ times by 4+ distinct sellers scores exactly as high
        // as an equivalent BUY deal would - repetition/breadth are resolved to the deal's own
        // (SELL) side, never unconditionally "buyer".
        DealMaterialityInput sellInput = baseInput(
            new BigDecimal("100000000.00"), new BigDecimal("100000000.00"), "SELL",
            4, 4, 0, 4, BigDecimal.ZERO, new BigDecimal("1000000")
        );
        DealMaterialityInput equivalentBuyInput = baseInput(
            new BigDecimal("100000000.00"), new BigDecimal("100000000.00"), "BUY",
            4, 4, 4, 0, new BigDecimal("1000000"), BigDecimal.ZERO
        );

        DealMaterialityResult sellResult = engine.calculate(sellInput, rules);
        DealMaterialityResult buyResult = engine.calculate(equivalentBuyInput, rules);

        assertThat(sellResult.materialityScore()).isEqualTo(buyResult.materialityScore());
        assertThat(sellResult.materialityScore()).isEqualTo(100.00);
        assertThat(sellResult.materialityLevel()).isEqualTo("VERY_HIGH");
        assertThat(sellResult.direction()).isEqualTo("SELL");
    }

    @Test
    void distinctBuyersAndSellersEvidenceIsNeverMixedRegardlessOfTheScoredDealsOwnDirection() {
        DealMaterialityInput input = baseInput(
            new BigDecimal("50000000.00"), new BigDecimal("10000000.00"), "SELL",
            2, 2, 3, 5, new BigDecimal("700000"), new BigDecimal("300000")
        );

        DealMaterialityResult result = engine.calculate(input, rules);

        assertThat(result.distinctBuyers20CalendarDays()).isEqualTo(3);
        assertThat(result.distinctSellers20CalendarDays()).isEqualTo(5);
    }

    @Test
    void ruleSetVersionIsPersistedOntoTheResult() {
        DealMaterialityInput input = baseInput(
            new BigDecimal("50000000.00"), new BigDecimal("10000000.00"), "BUY",
            1, 1, 1, 0, new BigDecimal("500000"), new BigDecimal("500000")
        );

        DealMaterialityResult result = engine.calculate(input, rules);

        assertThat(result.ruleVersion()).isEqualTo(rules.version());
    }

    @Test
    void reportedNetFlowStateBandsAtEachOfTheFiveBoundaries() {
        assertThat(flowStateFor("650000", "350000")).isEqualTo("STRONG_NET_BUYING"); // ratio +0.30
        assertThat(flowStateFor("625000", "375000")).isEqualTo("NET_BUYING");        // ratio +0.25 (boundary)
        assertThat(flowStateFor("575000", "425000")).isEqualTo("NET_BUYING");        // ratio +0.15
        assertThat(flowStateFor("550000", "450000")).isEqualTo("BALANCED");          // ratio +0.10 (boundary)
        assertThat(flowStateFor("500000", "500000")).isEqualTo("BALANCED");          // ratio  0.00
        assertThat(flowStateFor("450000", "550000")).isEqualTo("BALANCED");          // ratio -0.10 (boundary)
        assertThat(flowStateFor("425000", "575000")).isEqualTo("NET_SELLING");       // ratio -0.15
        assertThat(flowStateFor("375000", "625000")).isEqualTo("NET_SELLING");       // ratio -0.25 (boundary)
        assertThat(flowStateFor("350000", "650000")).isEqualTo("STRONG_NET_SELLING"); // ratio -0.30
    }

    private String flowStateFor(String reportedBuyValue, String reportedSellValue) {
        DealMaterialityInput input = baseInput(
            new BigDecimal("50000000.00"), new BigDecimal("10000000.00"), "BUY",
            1, 1, 1, 1, new BigDecimal(reportedBuyValue), new BigDecimal(reportedSellValue)
        );
        return engine.calculate(input, rules).reportedFlowState();
    }

    /** Mirrors V14's seeded rules exactly. */
    private static RuleSet buildRuleSet() {
        Rule adtvRatio = new Rule("deal-materiality-adtv-ratio", "dealToAdtvRatio", 1, List.of(
            new RuleCondition(RuleOperator.ALWAYS, 0, 10),
            new RuleCondition(RuleOperator.GTE, 0.10, 20),
            new RuleCondition(RuleOperator.GTE, 0.25, 25),
            new RuleCondition(RuleOperator.GTE, 0.50, 25),
            new RuleCondition(RuleOperator.GTE, 1.00, 20)
        ));
        Rule repetition = new Rule("deal-materiality-repetition", "sameSideClientDealCount20CalendarDays", 1, List.of(
            new RuleCondition(RuleOperator.EQ, 1, 20),
            new RuleCondition(RuleOperator.EQ, 2, 50),
            new RuleCondition(RuleOperator.EQ, 3, 75),
            new RuleCondition(RuleOperator.GTE, 4, 100)
        ));
        Rule breadth = new Rule("deal-materiality-breadth", "distinctSameSideClients20CalendarDays", 1, List.of(
            new RuleCondition(RuleOperator.EQ, 1, 20),
            new RuleCondition(RuleOperator.EQ, 2, 50),
            new RuleCondition(RuleOperator.EQ, 3, 75),
            new RuleCondition(RuleOperator.GTE, 4, 100)
        ));
        Rule blendAdtv = new Rule("deal-materiality-blend-adtv-ratio", "materialityAdtvRatioScore", 1, List.of(
            new RuleCondition(RuleOperator.ALWAYS, 0, 0.70)
        ));
        Rule blendRepetition = new Rule("deal-materiality-blend-repetition", "materialityRepetitionScore", 1, List.of(
            new RuleCondition(RuleOperator.ALWAYS, 0, 0.20)
        ));
        Rule blendBreadth = new Rule("deal-materiality-blend-breadth", "materialityBreadthScore", 1, List.of(
            new RuleCondition(RuleOperator.ALWAYS, 0, 0.10)
        ));

        return new RuleSet(1, List.of(adtvRatio, repetition, breadth, blendAdtv, blendRepetition, blendBreadth));
    }
}
