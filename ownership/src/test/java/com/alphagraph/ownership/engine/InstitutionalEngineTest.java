package com.alphagraph.ownership.engine;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.ownership.api.BulkDeal;
import com.alphagraph.ownership.api.DeliveryVolumeBar;
import com.alphagraph.ownership.api.InstitutionalEngineInput;
import com.alphagraph.ownership.api.InstitutionalFlowStatus;
import com.alphagraph.ownership.api.InstitutionalScore;
import com.alphagraph.ownership.api.PromoterStatus;
import com.alphagraph.ownership.api.ShareholdingPattern;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class InstitutionalEngineTest {

    private final InstitutionalEngine engine = new InstitutionalEngine();

    // Mirrors the 7 rules seeded by common's V5 migration (Module 1.7).
    private static RuleSet defaultRuleSet() {
        List<Rule> rules = List.of(
            new Rule("institutional-promoter-trend", "promoterChangePct", 1, List.of(
                new RuleCondition(RuleOperator.GT, 0.5, 1.0), new RuleCondition(RuleOperator.LT, -0.5, -1.0))),
            new Rule("institutional-fii-trend", "fiiChangePct", 1, List.of(
                new RuleCondition(RuleOperator.GT, 0.5, 1.0), new RuleCondition(RuleOperator.LT, -0.5, -1.0))),
            new Rule("institutional-dii-trend", "diiChangePct", 1, List.of(
                new RuleCondition(RuleOperator.GT, 0.5, 1.0), new RuleCondition(RuleOperator.LT, -0.5, -1.0))),
            new Rule("institutional-mf-trend", "mfChangePct", 1, List.of(
                new RuleCondition(RuleOperator.GT, 0.5, 1.0), new RuleCondition(RuleOperator.LT, -0.5, -1.0))),
            new Rule("institutional-delivery-high", "avgDeliveryPercentage", 1, List.of(
                new RuleCondition(RuleOperator.GT, 70, 1.0),
                new RuleCondition(RuleOperator.BETWEEN, 50, 70.0, 0.5))),
            new Rule("institutional-volume-expansion", "relativeVolume", 1,
                List.of(new RuleCondition(RuleOperator.GT, 1.5, 1.0))),
            new Rule("institutional-bulk-deal-flow", "netBulkDealQuantity", 1, List.of(
                new RuleCondition(RuleOperator.GT, 0, 1.0), new RuleCondition(RuleOperator.LT, 0, -1.0)))
        );
        return new RuleSet(1, rules);
    }

    private static ShareholdingPattern period(
        UUID instrumentId, LocalDate periodEnd, double promoter, double fii, double dii, Double mf
    ) {
        return new ShareholdingPattern(
            instrumentId, "TESTCO", periodEnd, BigDecimal.valueOf(promoter), BigDecimal.valueOf(fii),
            BigDecimal.valueOf(dii), mf == null ? null : BigDecimal.valueOf(mf), null
        );
    }

    private static DeliveryVolumeBar bar(LocalDate date, double deliveryPct, long volume) {
        return new DeliveryVolumeBar(date, BigDecimal.valueOf(deliveryPct), volume);
    }

    @Test
    void accumulatingPromoterWithHighDeliveryAndVolumeProducesAccumulationSignal() {
        UUID instrumentId = UUID.randomUUID();
        ShareholdingPattern prior = period(instrumentId, LocalDate.of(2026, 3, 31), 50.0, 15.0, 18.0, 8.0);
        ShareholdingPattern current = period(instrumentId, LocalDate.of(2026, 6, 30), 52.0, 17.0, 19.5, 8.0);

        List<DeliveryVolumeBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 6, 1);
        for (int i = 0; i < 21; i++) {
            long volume = (i == 20) ? 3000L : 1000L; // volume spike on the most recent bar
            bars.add(bar(date, 75.0, volume));
            date = date.plusDays(1);
        }

        InstitutionalEngineInput input = new InstitutionalEngineInput(
            instrumentId, "TESTCO", List.of(prior, current), bars, List.of()
        );
        InstitutionalScore score = engine.calculate(input, defaultRuleSet());

        // promoter +2.0pp, FII +2.0pp, DII +1.5pp - all comfortably above the 0.5pp noise band.
        assertThat(score.promoterStatus()).isEqualTo(PromoterStatus.ACCUMULATING);
        assertThat(score.fiiStatus()).isEqualTo(InstitutionalFlowStatus.BUYING);
        assertThat(score.diiStatus()).isEqualTo(InstitutionalFlowStatus.BUYING);
        assertThat(score.mfStatus()).isEqualTo(InstitutionalFlowStatus.STABLE); // unchanged at 8.0
        assertThat(score.avgDeliveryPercentage()).isEqualTo(75.0);
        assertThat(score.relativeVolume()).isEqualTo(3.0);
        assertThat(score.institutionalScore()).isGreaterThan(50.0);
    }

    @Test
    void singlePeriodLeavesTrendNullAndFallsBackToStable() {
        UUID instrumentId = UUID.randomUUID();
        ShareholdingPattern onlyPeriod = period(instrumentId, LocalDate.of(2026, 6, 30), 50.0, 15.0, 18.0, null);

        InstitutionalEngineInput input = new InstitutionalEngineInput(
            instrumentId, "TESTCO", List.of(onlyPeriod), List.of(), List.of()
        );
        InstitutionalScore score = engine.calculate(input, defaultRuleSet());

        assertThat(score.promoterChangePercentage()).isNull();
        assertThat(score.promoterStatus()).isEqualTo(PromoterStatus.STABLE);
        assertThat(score.avgDeliveryPercentage()).isNull();
        assertThat(score.relativeVolume()).isNull();
        assertThat(score.institutionalScore()).isEqualTo(50.0);
        assertThat(score.confidence()).isLessThan(100.0);
    }

    @Test
    void realBulkDealBuyingContributesPositively() {
        UUID instrumentId = UUID.randomUUID();
        ShareholdingPattern onlyPeriod = period(instrumentId, LocalDate.of(2026, 6, 30), 50.0, 15.0, 18.0, null);
        List<BulkDeal> deals = List.of(
            new BulkDeal(instrumentId, "TESTCO", LocalDate.of(2026, 7, 1), "SOME FUND", "BUY", 500000, BigDecimal.TEN, "BULK"),
            new BulkDeal(instrumentId, "TESTCO", LocalDate.of(2026, 7, 2), "ANOTHER FUND", "SELL", 100000, BigDecimal.TEN, "BULK")
        );

        InstitutionalEngineInput input = new InstitutionalEngineInput(
            instrumentId, "TESTCO", List.of(onlyPeriod), List.of(), deals
        );
        InstitutionalScore score = engine.calculate(input, defaultRuleSet());

        assertThat(score.netBulkDealQuantity()).isEqualTo(400000L);
        assertThat(score.institutionalScore()).isGreaterThan(50.0);
    }

    @Test
    void emptyShareholdingPeriodsThrows() {
        InstitutionalEngineInput input = new InstitutionalEngineInput(UUID.randomUUID(), "EMPTY", List.of(), List.of(), List.of());

        assertThatIllegalArgumentException().isThrownBy(() -> engine.calculate(input, defaultRuleSet()));
    }
}
