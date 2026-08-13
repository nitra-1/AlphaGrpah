package com.alphagraph.intelligence.priceadjustment;

import com.alphagraph.corporate.api.CorporateAction;
import com.alphagraph.market.api.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PriceAdjustmentEngineTest {

    private final PriceAdjustmentEngine engine = new PriceAdjustmentEngine();
    private final UUID instrumentId = UUID.randomUUID();

    @Test
    void leavesAPriceWithNoLaterActionsUnadjusted() {
        DailyPrice price = price(LocalDate.of(2026, 1, 10), "100.00");

        List<AdjustedDailyPrice> result = engine.apply(List.of(price), List.of());

        assertThat(result).hasSize(1);
        AdjustedDailyPrice adjusted = result.get(0);
        assertThat(adjusted.isAdjusted()).isFalse();
        assertThat(adjusted.rawClose()).isEqualByComparingTo("100.00");
        assertThat(adjusted.adjustedClose()).isEqualByComparingTo("100.00");
        assertThat(adjusted.cumulativeFactor()).isEqualByComparingTo("1");
    }

    @Test
    void doesNotAdjustAPriceOnOrAfterTheExDateItself() {
        // Ex-date IS the day the adjustment takes effect - a price recorded that same day or later
        // already reflects the new share count, so it must not be scaled again.
        DailyPrice onExDate = price(LocalDate.of(2026, 3, 1), "50.00");
        CorporateAction bonus = bonus(LocalDate.of(2026, 3, 1), 1, 1);

        List<AdjustedDailyPrice> result = engine.apply(List.of(onExDate), List.of(bonus));

        assertThat(result.get(0).isAdjusted()).isFalse();
        assertThat(result.get(0).adjustedClose()).isEqualByComparingTo("50.00");
    }

    @Test
    void halvesAHistoricalPriceForA1For1Bonus() {
        // 1:1 bonus -> holding doubles -> pre-bonus price should back-adjust to half.
        DailyPrice beforeBonus = price(LocalDate.of(2026, 1, 10), "200.00");
        CorporateAction bonus = bonus(LocalDate.of(2026, 2, 1), 1, 1);

        List<AdjustedDailyPrice> result = engine.apply(List.of(beforeBonus), List.of(bonus));

        AdjustedDailyPrice adjusted = result.get(0);
        assertThat(adjusted.isAdjusted()).isTrue();
        assertThat(adjusted.cumulativeFactor().doubleValue()).isCloseTo(0.5, within(0.000001));
        assertThat(adjusted.adjustedClose()).isEqualByComparingTo("100.00");
        assertThat(adjusted.appliedActions()).hasSize(1);
        assertThat(adjusted.appliedActions().get(0).actionType()).isEqualTo("BONUS");
    }

    @Test
    void quintsAHistoricalPriceDownForA5For1Split() {
        // 5:1 split -> 1 old share becomes 5 new shares -> pre-split price back-adjusts to 1/5th.
        DailyPrice beforeSplit = price(LocalDate.of(2026, 1, 10), "500.00");
        CorporateAction split = split(LocalDate.of(2026, 2, 1), 5, 1);

        List<AdjustedDailyPrice> result = engine.apply(List.of(beforeSplit), List.of(split));

        AdjustedDailyPrice adjusted = result.get(0);
        assertThat(adjusted.cumulativeFactor().doubleValue()).isCloseTo(0.2, within(0.000001));
        assertThat(adjusted.adjustedClose()).isEqualByComparingTo("100.00");
        assertThat(adjusted.appliedActions().get(0).actionType()).isEqualTo("SPLIT");
    }

    @Test
    void compoundsMultipleLaterActionsMultiplicatively() {
        // A price before both a 1:1 bonus (x0.5) and a later 5:1 split (x0.2) should carry both -
        // cumulative factor 0.1, not just the nearer one.
        DailyPrice beforeBoth = price(LocalDate.of(2026, 1, 1), "1000.00");
        CorporateAction bonus = bonus(LocalDate.of(2026, 2, 1), 1, 1);
        CorporateAction split = split(LocalDate.of(2026, 4, 1), 5, 1);

        List<AdjustedDailyPrice> result = engine.apply(List.of(beforeBoth), List.of(split, bonus));

        AdjustedDailyPrice adjusted = result.get(0);
        assertThat(adjusted.cumulativeFactor().doubleValue()).isCloseTo(0.1, within(0.000001));
        assertThat(adjusted.adjustedClose()).isEqualByComparingTo("100.00");
        assertThat(adjusted.appliedActions()).hasSize(2);
    }

    @Test
    void onlyAppliesActionsStrictlyAfterEachPricesOwnDate() {
        // Two prices straddling one bonus: the earlier one adjusts, the later one (post-bonus) doesn't.
        DailyPrice before = price(LocalDate.of(2026, 1, 10), "200.00");
        DailyPrice after = price(LocalDate.of(2026, 2, 10), "100.00");
        CorporateAction bonus = bonus(LocalDate.of(2026, 2, 1), 1, 1);

        List<AdjustedDailyPrice> result = engine.apply(List.of(before, after), List.of(bonus));

        assertThat(result.get(0).isAdjusted()).isTrue();
        assertThat(result.get(0).adjustedClose()).isEqualByComparingTo("100.00");
        assertThat(result.get(1).isAdjusted()).isFalse();
        assertThat(result.get(1).adjustedClose()).isEqualByComparingTo("100.00");
    }

    @Test
    void ignoresDividendActionsEntirely() {
        DailyPrice priced = price(LocalDate.of(2026, 1, 10), "100.00");
        CorporateAction dividend = new CorporateAction(
            instrumentId, "TEST", "DIVIDEND", LocalDate.of(2026, 2, 1), null, null, new BigDecimal("5.00"), null, null, null, null
        );

        List<AdjustedDailyPrice> result = engine.apply(List.of(priced), List.of(dividend));

        assertThat(result.get(0).isAdjusted()).isFalse();
    }

    @Test
    void throwsRatherThanSilentlySkippingAnActionWithAMissingRatio() {
        DailyPrice priced = price(LocalDate.of(2026, 1, 10), "100.00");
        CorporateAction malformedBonus = new CorporateAction(
            instrumentId, "TEST", "BONUS", LocalDate.of(2026, 2, 1), null, null, null, null, null, null, null
        );

        assertThatIllegalStateExceptionThrown(() -> engine.apply(List.of(priced), List.of(malformedBonus)));
    }

    private static void assertThatIllegalStateExceptionThrown(Runnable action) {
        org.assertj.core.api.Assertions.assertThatThrownBy(action::run).isInstanceOf(IllegalStateException.class);
    }

    private DailyPrice price(LocalDate tradeDate, String close) {
        BigDecimal closePrice = new BigDecimal(close);
        return new DailyPrice(instrumentId, "TEST", tradeDate, closePrice, closePrice, closePrice, closePrice, 1000L, new BigDecimal("50.00"));
    }

    private CorporateAction bonus(LocalDate exDate, int numerator, int denominator) {
        return new CorporateAction(instrumentId, "TEST", "BONUS", exDate, null, null, null, numerator, denominator, null, null);
    }

    private CorporateAction split(LocalDate exDate, int numerator, int denominator) {
        return new CorporateAction(instrumentId, "TEST", "SPLIT", exDate, null, null, null, numerator, denominator, null, null);
    }
}
