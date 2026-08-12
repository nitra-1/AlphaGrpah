package com.alphagraph.learning.outcomes;

import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.intelligence.priceadjustment.AdjustedDailyPrice;
import com.alphagraph.learning.snapshot.DecisionSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ForwardOutcomeEngineTest {

    private final ForwardOutcomeEngine engine = new ForwardOutcomeEngine();
    private final UUID instrumentId = UUID.randomUUID();
    private final LocalDate decisionDate = LocalDate.of(2026, 1, 1);

    @Test
    void emitsNothingWhenTheDecisionDateHasNoMatchingPriceRow() {
        DecisionSnapshot snapshot = snapshot(DecisionRating.BUY, DecisionRating.HOLD);
        List<AdjustedDailyPrice> prices = pricesFrom(decisionDate.plusDays(1), "100", "101", "102");

        List<ForwardOutcome> result = engine.computeOutcomes(snapshot, prices, Set.of());

        assertThat(result).isEmpty();
    }

    @Test
    void emitsOnlyHorizonsWithEnoughElapsedTradingDays() {
        // 5 trading days after the decision date exist (indices 0..5, 6 rows), but not 10.
        DecisionSnapshot snapshot = snapshot(DecisionRating.BUY, DecisionRating.HOLD);
        List<AdjustedDailyPrice> prices = pricesFrom(decisionDate, "100", "101", "102", "103", "104", "105", "106");

        List<ForwardOutcome> result = engine.computeOutcomes(snapshot, prices, Set.of());

        assertThat(result).extracting(ForwardOutcome::horizonDays).containsExactly(5);
    }

    @Test
    void skipsHorizonsAlreadyComputed() {
        DecisionSnapshot snapshot = snapshot(DecisionRating.BUY, DecisionRating.HOLD);
        List<AdjustedDailyPrice> prices = pricesFrom(decisionDate, sixtyOnePricesStartingAt100());

        List<ForwardOutcome> result = engine.computeOutcomes(snapshot, prices, Set.of(5, 10));

        assertThat(result).extracting(ForwardOutcome::horizonDays).containsExactlyInAnyOrder(20, 60);
    }

    @Test
    void buyRatingIsCorrectWhenForwardReturnIsPositive() {
        DecisionSnapshot snapshot = snapshot(DecisionRating.BUY, DecisionRating.HOLD);
        List<AdjustedDailyPrice> prices = pricesFrom(decisionDate, "100", "101", "102", "103", "104", "110");

        ForwardOutcome outcome = engine.computeOutcomes(snapshot, prices, Set.of()).get(0);

        assertThat(outcome.forwardReturnPercentage()).isEqualByComparingTo("10.00");
        assertThat(outcome.swingDirectionallyCorrect()).isTrue();
    }

    @Test
    void avoidRatingIsCorrectWhenForwardReturnIsNegative() {
        DecisionSnapshot snapshot = snapshot(DecisionRating.AVOID, DecisionRating.HOLD);
        List<AdjustedDailyPrice> prices = pricesFrom(decisionDate, "100", "99", "98", "97", "96", "90");

        ForwardOutcome outcome = engine.computeOutcomes(snapshot, prices, Set.of()).get(0);

        assertThat(outcome.forwardReturnPercentage()).isEqualByComparingTo("-10.00");
        assertThat(outcome.swingDirectionallyCorrect()).isTrue();
    }

    @Test
    void buyRatingIsWrongWhenForwardReturnIsNegative() {
        DecisionSnapshot snapshot = snapshot(DecisionRating.STRONG_BUY, DecisionRating.HOLD);
        List<AdjustedDailyPrice> prices = pricesFrom(decisionDate, "100", "99", "98", "97", "96", "90");

        ForwardOutcome outcome = engine.computeOutcomes(snapshot, prices, Set.of()).get(0);

        assertThat(outcome.swingDirectionallyCorrect()).isFalse();
    }

    @Test
    void holdRatingIsExcludedFromDirectionalScoring() {
        DecisionSnapshot snapshot = snapshot(DecisionRating.HOLD, DecisionRating.HOLD);
        List<AdjustedDailyPrice> prices = pricesFrom(decisionDate, "100", "101", "102", "103", "104", "110");

        ForwardOutcome outcome = engine.computeOutcomes(snapshot, prices, Set.of()).get(0);

        assertThat(outcome.swingDirectionallyCorrect()).isNull();
        assertThat(outcome.longTermDirectionallyCorrect()).isNull();
    }

    @Test
    void domainScoreAbove50IsBullishSignal() {
        DecisionSnapshot snapshot = snapshotWithTechnicalScore(75.0);
        List<AdjustedDailyPrice> prices = pricesFrom(decisionDate, "100", "101", "102", "103", "104", "110");

        ForwardOutcome outcome = engine.computeOutcomes(snapshot, prices, Set.of()).get(0);

        assertThat(outcome.technicalSignalCorrect()).isTrue();
    }

    @Test
    void domainScoreBelow50IsBearishSignal() {
        DecisionSnapshot snapshot = snapshotWithTechnicalScore(25.0);
        List<AdjustedDailyPrice> prices = pricesFrom(decisionDate, "100", "101", "102", "103", "104", "110");

        ForwardOutcome outcome = engine.computeOutcomes(snapshot, prices, Set.of()).get(0);

        assertThat(outcome.technicalSignalCorrect()).isFalse();
    }

    @Test
    void domainScoreOfExactly50OrNullIsNotApplicable() {
        DecisionSnapshot atFifty = snapshotWithTechnicalScore(50.0);
        DecisionSnapshot missing = snapshotWithTechnicalScore(null);
        List<AdjustedDailyPrice> prices = pricesFrom(decisionDate, "100", "101", "102", "103", "104", "110");

        assertThat(engine.computeOutcomes(atFifty, prices, Set.of()).get(0).technicalSignalCorrect()).isNull();
        assertThat(engine.computeOutcomes(missing, prices, Set.of()).get(0).technicalSignalCorrect()).isNull();
    }

    private DecisionSnapshot snapshot(DecisionRating swingRating, DecisionRating longTermRating) {
        return new DecisionSnapshot(
            instrumentId, "TEST", decisionDate,
            60.0, swingRating, 1, 60.0, longTermRating, 1,
            null, null, null, null, null, null,
            80.0, 1, Instant.now(), Instant.now()
        );
    }

    private DecisionSnapshot snapshotWithTechnicalScore(Double technicalScore) {
        return new DecisionSnapshot(
            instrumentId, "TEST", decisionDate,
            60.0, DecisionRating.HOLD, 1, 60.0, DecisionRating.HOLD, 1,
            technicalScore, null, null, null, null, null,
            80.0, 1, Instant.now(), Instant.now()
        );
    }

    private List<AdjustedDailyPrice> pricesFrom(LocalDate startDate, String... closes) {
        List<AdjustedDailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            BigDecimal close = new BigDecimal(closes[i]);
            prices.add(new AdjustedDailyPrice(instrumentId, "TEST", startDate.plusDays(i), close, close, BigDecimal.ONE, List.of()));
        }
        return prices;
    }

    private String[] sixtyOnePricesStartingAt100() {
        String[] closes = new String[61];
        for (int i = 0; i < 61; i++) {
            closes[i] = String.valueOf(100 + i);
        }
        return closes;
    }
}
