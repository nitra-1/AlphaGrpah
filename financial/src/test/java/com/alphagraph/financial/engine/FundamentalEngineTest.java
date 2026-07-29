package com.alphagraph.financial.engine;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.financial.api.BusinessGrowth;
import com.alphagraph.financial.api.FinancialQuality;
import com.alphagraph.financial.api.FinancialResult;
import com.alphagraph.financial.api.FundamentalEngineInput;
import com.alphagraph.financial.api.FundamentalScore;
import com.alphagraph.financial.api.Profitability;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FundamentalEngineTest {

    private final FundamentalEngine engine = new FundamentalEngine();

    // Mirrors the 8 rules seeded by common's V4 migration (Module 1.6).
    private static RuleSet defaultRuleSet() {
        List<Rule> rules = List.of(
            new Rule("fundamental-revenue-growth", "revenueGrowthPct", 1, List.of(
                new RuleCondition(RuleOperator.GT, 15, 1.0),
                new RuleCondition(RuleOperator.BETWEEN, 0, 15.0, 0.5),
                new RuleCondition(RuleOperator.LT, 0, -1.0)
            )),
            new Rule("fundamental-pat-growth", "patGrowthPct", 1, List.of(
                new RuleCondition(RuleOperator.GT, 15, 1.0),
                new RuleCondition(RuleOperator.BETWEEN, 0, 15.0, 0.5),
                new RuleCondition(RuleOperator.LT, 0, -1.0)
            )),
            new Rule("fundamental-roe-quality", "roePercentage", 1, List.of(
                new RuleCondition(RuleOperator.GT, 20, 1.0),
                new RuleCondition(RuleOperator.BETWEEN, 10, 20.0, 0.5),
                new RuleCondition(RuleOperator.LT, 10, -0.5)
            )),
            new Rule("fundamental-roce-quality", "rocePercentage", 1, List.of(
                new RuleCondition(RuleOperator.GT, 20, 1.0),
                new RuleCondition(RuleOperator.BETWEEN, 10, 20.0, 0.5),
                new RuleCondition(RuleOperator.LT, 10, -0.5)
            )),
            new Rule("fundamental-net-margin-quality", "netMarginPercentage", 1, List.of(
                new RuleCondition(RuleOperator.GT, 15, 1.0),
                new RuleCondition(RuleOperator.BETWEEN, 5, 15.0, 0.5),
                new RuleCondition(RuleOperator.LT, 5, -0.5)
            )),
            new Rule("fundamental-asset-turnover-efficiency", "assetTurnover", 1,
                List.of(new RuleCondition(RuleOperator.GT, 1.0, 1.0))),
            new Rule("fundamental-interest-coverage-leverage", "interestCoverage", 1, List.of(
                new RuleCondition(RuleOperator.GT, 5, 1.0),
                new RuleCondition(RuleOperator.BETWEEN, 2, 5.0, 0.3),
                new RuleCondition(RuleOperator.LT, 2, -1.0)
            )),
            new Rule("fundamental-debt-equity-leverage", "debtToEquity", 1, List.of(
                new RuleCondition(RuleOperator.LT, 0.5, 1.0),
                new RuleCondition(RuleOperator.GT, 1.5, -1.0)
            ))
        );
        return new RuleSet(1, rules);
    }

    private static FinancialResult period(
        UUID instrumentId, LocalDate periodEnd, double sales, double pat, Double netMarginPct,
        Double totalAssets, Double totalDebt, Double totalEquity, Double ebit, Double interestExpense
    ) {
        return new FinancialResult(
            instrumentId, "TESTCO", periodEnd, "QUARTERLY",
            BigDecimal.valueOf(sales), BigDecimal.valueOf(pat), null, BigDecimal.valueOf(22), BigDecimal.valueOf(25),
            null, netMarginPct == null ? null : BigDecimal.valueOf(netMarginPct), null,
            totalAssets == null ? null : BigDecimal.valueOf(totalAssets), null, null,
            totalDebt == null ? null : BigDecimal.valueOf(totalDebt),
            totalEquity == null ? null : BigDecimal.valueOf(totalEquity),
            interestExpense == null ? null : BigDecimal.valueOf(interestExpense),
            ebit == null ? null : BigDecimal.valueOf(ebit)
        );
    }

    @Test
    void strongYoyGrowthWithHighQualityAndLowLeverageProducesExcellentGrowthAndStrongQuality() {
        UUID instrumentId = UUID.randomUUID();
        FinancialResult prior = period(instrumentId, LocalDate.of(2024, 3, 31), 100.0, 10.0, 12.0, null, null, null, null, null);
        FinancialResult current = period(
            instrumentId, LocalDate.of(2025, 3, 31), 130.0, 14.0, 18.0,
            100.0, 20.0, 200.0, 50.0, 5.0
        );

        FundamentalEngineInput input = new FundamentalEngineInput(instrumentId, "TESTCO", List.of(prior, current));
        FundamentalScore score = engine.calculate(input, defaultRuleSet());

        // revenue growth = 30%, PAT growth = 40% - both comfortably above the 15% "excellent" bar.
        assertThat(score.businessGrowth()).isEqualTo(BusinessGrowth.EXCELLENT);
        assertThat(score.revenueGrowthPercentage()).isEqualTo(30.0);
        assertThat(score.patGrowthPercentage()).isEqualTo(40.0);
        // ROE=22, debt/equity=0.1 - both comfortably in the "strong quality" band.
        assertThat(score.financialQuality()).isEqualTo(FinancialQuality.STRONG);
        assertThat(score.debtToEquity()).isEqualTo(0.1);
        assertThat(score.interestCoverage()).isEqualTo(10.0);
        assertThat(score.assetTurnover()).isEqualTo(1.3);
        assertThat(score.financialScore()).isGreaterThan(50.0);
        assertThat(score.profitability()).isEqualTo(Profitability.IMPROVING);
    }

    @Test
    void singlePeriodLeavesGrowthNullAndFallsBackToNeutralClassifications() {
        UUID instrumentId = UUID.randomUUID();
        FinancialResult onlyPeriod = period(instrumentId, LocalDate.of(2025, 3, 31), 100.0, 10.0, null, null, null, null, null, null);

        FundamentalEngineInput input = new FundamentalEngineInput(instrumentId, "TESTCO", List.of(onlyPeriod));
        FundamentalScore score = engine.calculate(input, defaultRuleSet());

        assertThat(score.revenueGrowthPercentage()).isNull();
        assertThat(score.patGrowthPercentage()).isNull();
        assertThat(score.businessGrowth()).isEqualTo(BusinessGrowth.MODERATE);
        assertThat(score.profitability()).isEqualTo(Profitability.STABLE);
        assertThat(score.confidence()).isLessThan(100.0);
    }

    @Test
    void decliningRevenueAndPatProducesDecliningGrowth() {
        UUID instrumentId = UUID.randomUUID();
        FinancialResult prior = period(instrumentId, LocalDate.of(2024, 3, 31), 100.0, 20.0, 20.0, null, null, null, null, null);
        FinancialResult current = period(instrumentId, LocalDate.of(2025, 3, 31), 85.0, 15.0, 17.6, null, null, null, null, null);

        FundamentalEngineInput input = new FundamentalEngineInput(instrumentId, "TESTCO", List.of(prior, current));
        FundamentalScore score = engine.calculate(input, defaultRuleSet());

        // revenue -15%, PAT -25% - average -20%, below the -10% "declining" threshold. Not
        // asserting financialScore here: the helper's fixed ROE=22/ROCE=25 quality signals are
        // strong enough on their own to keep the overall score positive even with growth
        // penalties - businessGrowth is this test's actual target, not the composite score.
        assertThat(score.businessGrowth()).isEqualTo(BusinessGrowth.DECLINING);
        assertThat(score.revenueGrowthPercentage()).isEqualTo(-15.0);
        assertThat(score.patGrowthPercentage()).isEqualTo(-25.0);
    }

    @Test
    void emptyPeriodsThrows() {
        FundamentalEngineInput input = new FundamentalEngineInput(UUID.randomUUID(), "EMPTY", List.of());

        assertThatIllegalArgumentException().isThrownBy(() -> engine.calculate(input, defaultRuleSet()));
    }
}
