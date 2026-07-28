package com.alphagraph.technical.engine;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.technical.api.BreakoutStatus;
import com.alphagraph.technical.api.DailyBar;
import com.alphagraph.technical.api.TechnicalEngineInput;
import com.alphagraph.technical.api.TechnicalScore;
import com.alphagraph.technical.api.Trend;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TechnicalEngineTest {

    private final TechnicalEngine engine = new TechnicalEngine();

    // Mirrors the 7 rules seeded by common's V3 migration (Module 1.5), so this test exercises
    // the same rule set the live app runs with, not a bespoke test-only one.
    private static RuleSet defaultRuleSet() {
        List<Rule> rules = List.of(
            new Rule("technical-price-above-sma50", "priceVsSma50Pct", 1,
                List.of(new RuleCondition(RuleOperator.GT, 0, 1.0))),
            new Rule("technical-price-above-sma200", "priceVsSma200Pct", 1,
                List.of(new RuleCondition(RuleOperator.GT, 0, 1.0))),
            new Rule("technical-rsi-momentum", "rsi14", 1, List.of(
                new RuleCondition(RuleOperator.BETWEEN, 50, 70.0, 1.0),
                new RuleCondition(RuleOperator.GT, 70, -0.5),
                new RuleCondition(RuleOperator.LT, 30, -1.0)
            )),
            new Rule("technical-macd-bullish", "macdHistogram", 1,
                List.of(new RuleCondition(RuleOperator.GT, 0, 1.0))),
            new Rule("technical-adx-trending", "adx14", 1,
                List.of(new RuleCondition(RuleOperator.GT, 25, 1.0))),
            new Rule("technical-relative-volume", "relativeVolume", 1,
                List.of(new RuleCondition(RuleOperator.GT, 1.5, 1.0))),
            new Rule("technical-obv-rising", "obvSlope", 1,
                List.of(new RuleCondition(RuleOperator.GT, 0, 1.0)))
        );
        return new RuleSet(1, rules);
    }

    private static DailyBar bar(LocalDate date, double close, long volume) {
        // High/low offset (0.4) is deliberately smaller than the daily price increment (1.0) used
        // by the uptrend test below, so each day's high still clears the previous day's high by
        // a visible margin instead of landing exactly on it (which would make the breakout check
        // a coin flip on floating-point/off-by-one boundaries rather than a clear break).
        BigDecimal closePrice = BigDecimal.valueOf(close);
        BigDecimal high = BigDecimal.valueOf(close + 0.4);
        BigDecimal low = BigDecimal.valueOf(close - 0.4);
        return new DailyBar(date, closePrice, high, low, closePrice, volume, BigDecimal.valueOf(50));
    }

    @Test
    void steadyUptrendWithHighVolumeProducesUptrendAndAboveNeutralScore() {
        List<DailyBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 1, 1);
        double price = 100.0;
        for (int i = 0; i < 70; i++) {
            // Skip weekends so trade dates look like a real market calendar.
            while (date.getDayOfWeek().getValue() > 5) {
                date = date.plusDays(1);
            }
            long volume = (i == 69) ? 5000L : 1000L; // volume spike on the final (most recent) bar
            bars.add(bar(date, price, volume));
            price += 1.0;
            date = date.plusDays(1);
        }

        TechnicalEngineInput input = new TechnicalEngineInput(UUID.randomUUID(), "TESTCO", bars);
        TechnicalScore score = engine.calculate(input, defaultRuleSet());

        assertThat(score.trend()).isIn(Trend.UPTREND, Trend.STRONG_UPTREND);
        assertThat(score.marketBehaviourScore()).isGreaterThan(50.0);
        assertThat(score.sma20()).isNotNull();
        assertThat(score.sma200()).isNull(); // only 70 bars - correctly insufficient for SMA200
        assertThat(score.breakoutStatus()).isEqualTo(BreakoutStatus.CONFIRMED);
    }

    @Test
    void flatPricesProduceSidewaysAndNeutralScore() {
        List<DailyBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 70; i++) {
            while (date.getDayOfWeek().getValue() > 5) {
                date = date.plusDays(1);
            }
            bars.add(bar(date, 100.0, 1000L));
            date = date.plusDays(1);
        }

        TechnicalEngineInput input = new TechnicalEngineInput(UUID.randomUUID(), "FLATCO", bars);
        TechnicalScore score = engine.calculate(input, defaultRuleSet());

        // Not asserting an exact score here: a perfectly flat series is a genuine RSI edge case
        // (avgGain and avgLoss are both exactly zero, so different textbook conventions disagree
        // on whether that's neutral-50 or the same 100 used for "all gains, no losses"). What
        // matters for this test is that no direction is being read into pure noise-free flatness.
        assertThat(score.trend()).isEqualTo(Trend.SIDEWAYS);
        assertThat(score.marketBehaviourScore()).isBetween(35.0, 65.0);
    }

    @Test
    void emptyBarsThrows() {
        TechnicalEngineInput input = new TechnicalEngineInput(UUID.randomUUID(), "EMPTY", List.of());

        assertThatIllegalArgumentException().isThrownBy(() -> engine.calculate(input, defaultRuleSet()));
    }
}
