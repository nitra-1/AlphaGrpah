package com.alphagraph.corporate.orderbook;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.corporate.api.OrderBookEntry;
import com.alphagraph.corporate.api.OrderBookSnapshot;
import com.alphagraph.corporate.api.OrderLifecycleStage;
import com.alphagraph.corporate.api.OrderQuality;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Mirrors the seeded orderbook-* rules (common/V8) exactly, so the classification thresholds tested here match production. */
class OrderBookAggregationEngineTest {

    private final OrderBookAggregationEngine engine = new OrderBookAggregationEngine();
    private final UUID instrumentId = UUID.randomUUID();
    private final LocalDate asOfDate = LocalDate.of(2026, 6, 1);

    private static final RuleSet RULES = new RuleSet(1, List.of(
        new Rule("orderbook-growth", "orderBookGrowthPct", 1, List.of(
            new RuleCondition(RuleOperator.GT, 20, null, 1.5),
            new RuleCondition(RuleOperator.LT, 0, null, -1.5)
        )),
        new Rule("orderbook-execution-visibility", "executionVisibilityYears", 1, List.of(
            new RuleCondition(RuleOperator.GT, 2, null, 1.0),
            new RuleCondition(RuleOperator.LT, 0.5, null, -1.0)
        )),
        new Rule("orderbook-order-count", "orderCount", 1, List.of(
            new RuleCondition(RuleOperator.GTE, 3, null, 0.5),
            new RuleCondition(RuleOperator.LTE, 1, null, -0.5)
        ))
    ));

    @Test
    void netsOrderValueAcrossAddsAndSubtracts() {
        List<OrderBookEntry> entries = List.of(
            entry(OrderLifecycleStage.NEW_ORDER, 1000.0, null, null),
            entry(OrderLifecycleStage.NEW_ORDER, 500.0, null, null),
            entry(OrderLifecycleStage.COMPLETION, 300.0, null, null)
        );

        OrderBookSnapshot snapshot = engine.calculate(input(entries, null), RULES);

        assertThat(snapshot.currentOrderBookCrore()).isEqualTo(1200.0);
    }

    @Test
    void growthIsNullWithoutAPreviousSnapshot() {
        List<OrderBookEntry> entries = List.of(entry(OrderLifecycleStage.NEW_ORDER, 1000.0, null, null));

        OrderBookSnapshot snapshot = engine.calculate(input(entries, null), RULES);

        assertThat(snapshot.orderBookGrowthPct()).isNull();
    }

    @Test
    void growthIsComputedAgainstThePreviousSnapshot() {
        List<OrderBookEntry> entries = List.of(entry(OrderLifecycleStage.NEW_ORDER, 1200.0, null, null));

        OrderBookSnapshot snapshot = engine.calculate(input(entries, 1000.0), RULES);

        assertThat(snapshot.orderBookGrowthPct()).isEqualTo(20.0);
    }

    @Test
    void executionVisibilityIsValueWeightedAverageOfRemainingYears() {
        // 1000 Cr with 4 years remaining, 1000 Cr with 2 years remaining -> weighted avg 3 years
        List<OrderBookEntry> entries = List.of(
            entry(OrderLifecycleStage.NEW_ORDER, 1000.0, "2026", "2030"),
            entry(OrderLifecycleStage.NEW_ORDER, 1000.0, "2026", "2028")
        );

        OrderBookSnapshot snapshot = engine.calculate(input(entries, null), RULES);

        assertThat(snapshot.executionVisibilityYears()).isEqualTo(3.0);
    }

    @Test
    void excellentQualityWhenAllMetricsAreStrong() {
        List<OrderBookEntry> entries = List.of(
            entry(OrderLifecycleStage.NEW_ORDER, 1000.0, "2026", "2030"),
            entry(OrderLifecycleStage.NEW_ORDER, 1000.0, "2026", "2030"),
            entry(OrderLifecycleStage.NEW_ORDER, 1000.0, "2026", "2030")
        );

        OrderBookSnapshot snapshot = engine.calculate(input(entries, 500.0), RULES);

        assertThat(snapshot.orderQuality()).isEqualTo(OrderQuality.EXCELLENT);
        assertThat(snapshot.qualityScore()).isEqualTo(80.0);
    }

    @Test
    void poorQualityWhenAllMetricsAreWeak() {
        List<OrderBookEntry> entries = List.of(entry(OrderLifecycleStage.NEW_ORDER, 100.0, "2020", "2021"));

        OrderBookSnapshot snapshot = engine.calculate(input(entries, 1000.0), RULES);

        assertThat(snapshot.orderQuality()).isEqualTo(OrderQuality.POOR);
    }

    @Test
    void emptyLedgerYieldsZeroMetricsNotAnException() {
        OrderBookSnapshot snapshot = engine.calculate(input(List.of(), null), RULES);

        assertThat(snapshot.currentOrderBookCrore()).isEqualTo(0.0);
        assertThat(snapshot.executionVisibilityYears()).isEqualTo(0.0);
        assertThat(snapshot.orderCount()).isEqualTo(0);
    }

    private OrderBookAggregationInput input(List<OrderBookEntry> entries, Double previousValue) {
        return new OrderBookAggregationInput(instrumentId, "TEST", entries, previousValue, asOfDate);
    }

    private OrderBookEntry entry(OrderLifecycleStage stage, Double valueCrore, String start, String end) {
        return new OrderBookEntry(
            UUID.randomUUID(), UUID.randomUUID(), instrumentId, "TEST",
            UUID.randomUUID(), valueCrore, "Unit", start, end, null, null, null, stage, Instant.now()
        );
    }
}
