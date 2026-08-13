package com.alphagraph.learning.outcomes;

import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.intelligence.priceadjustment.AdjustedDailyPrice;
import com.alphagraph.intelligence.priceadjustment.PriceAdjustmentService;
import com.alphagraph.learning.snapshot.DecisionSnapshot;
import com.alphagraph.reference.instrument.InstrumentReader;
import com.alphagraph.reference.instrument.SectorBenchmarkReader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BenchmarkReturnCalculator's own instrument/sector-benchmark lookups are mocked out to always
 * return empty here, so market/sector-relative fields resolve to UNAVAILABLE (null) throughout -
 * that behavior is covered by BenchmarkReturnCalculatorTest instead. This class stays focused on
 * the original absolute-return/directional-correctness contract plus MFE/MAE.
 */
class ForwardOutcomeEngineTest {

    private final InstrumentReader instrumentReader = mock(InstrumentReader.class);
    private final SectorBenchmarkReader sectorBenchmarkReader = mock(SectorBenchmarkReader.class);
    private final PriceAdjustmentService priceAdjustmentService = mock(PriceAdjustmentService.class);
    private final BenchmarkReturnCalculator benchmarkReturnCalculator =
        new BenchmarkReturnCalculator(instrumentReader, sectorBenchmarkReader, priceAdjustmentService, "NIFTY50");
    private final ForwardOutcomeEngine engine = new ForwardOutcomeEngine(
        new AbsoluteReturnCalculator(), benchmarkReturnCalculator, new ExcursionCalculator(), priceAdjustmentService
    );
    private final UUID instrumentId = UUID.randomUUID();
    private final LocalDate decisionDate = LocalDate.of(2026, 1, 1);

    @org.junit.jupiter.api.BeforeEach
    void noBenchmarksAndNoCorporateActionsByDefault() {
        when(instrumentReader.findIdBySymbol(any())).thenReturn(Optional.empty());
        when(sectorBenchmarkReader.findBenchmarkInstrumentIdForInstrument(any())).thenReturn(Optional.empty());
        when(priceAdjustmentService.findPriceAffectingActions(any())).thenReturn(List.of());
    }

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
        assertThat(outcome.status()).isEqualTo("CURRENT");
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

    @Test
    void recomputeSingleOutcomeRebuildsExactlyTheRequestedHorizonFromScratch() {
        DecisionSnapshot snapshot = snapshot(DecisionRating.BUY, DecisionRating.HOLD);
        List<AdjustedDailyPrice> prices = pricesFrom(decisionDate, "100", "101", "102", "103", "104", "110");

        ForwardOutcome recomputed = engine.recomputeSingleOutcome(snapshot, prices, 5);

        assertThat(recomputed).isNotNull();
        assertThat(recomputed.horizonDays()).isEqualTo(5);
        assertThat(recomputed.forwardReturnPercentage()).isEqualByComparingTo("10.00");
        assertThat(recomputed.status()).isEqualTo("CURRENT");
    }

    @Test
    void mfeAndMaeReflectTheHighestHighAndLowestLowOverTheHorizonNotJustTheClose() {
        DecisionSnapshot snapshot = snapshot(DecisionRating.BUY, DecisionRating.HOLD);
        // Reference close 100. Over the 5-day horizon the path spikes to 120 (day 3) and dips to 92 (day 4)
        // before ending at 108 - MFE/MAE must see the spike/dip, not just the day-5 close.
        List<AdjustedDailyPrice> prices = new ArrayList<>();
        prices.add(priceWithRange(decisionDate, "100", "100", "100"));
        prices.add(priceWithRange(decisionDate.plusDays(1), "103", "105", "101"));
        prices.add(priceWithRange(decisionDate.plusDays(2), "106", "108", "104"));
        prices.add(priceWithRange(decisionDate.plusDays(3), "110", "120", "109"));
        prices.add(priceWithRange(decisionDate.plusDays(4), "95", "111", "92"));
        prices.add(priceWithRange(decisionDate.plusDays(5), "108", "112", "107"));

        ForwardOutcome outcome = engine.computeOutcomes(snapshot, prices, Set.of()).get(0);

        assertThat(outcome.mfePercentage()).isEqualByComparingTo("20.00");
        assertThat(outcome.maePercentage()).isEqualByComparingTo("-8.00");
    }

    private DecisionSnapshot snapshot(DecisionRating swingRating, DecisionRating longTermRating) {
        return new DecisionSnapshot(
            instrumentId, "TEST", decisionDate,
            60.0, swingRating, 1, 60.0, longTermRating, 1,
            null, null, null, null, null, null,
            80.0, 1, Instant.now(),
            null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            Instant.now()
        );
    }

    private DecisionSnapshot snapshotWithTechnicalScore(Double technicalScore) {
        return new DecisionSnapshot(
            instrumentId, "TEST", decisionDate,
            60.0, DecisionRating.HOLD, 1, 60.0, DecisionRating.HOLD, 1,
            technicalScore, null, null, null, null, null,
            80.0, 1, Instant.now(),
            null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            Instant.now()
        );
    }

    private List<AdjustedDailyPrice> pricesFrom(LocalDate startDate, String... closes) {
        List<AdjustedDailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            prices.add(priceWithRange(startDate.plusDays(i), closes[i], closes[i], closes[i]));
        }
        return prices;
    }

    private AdjustedDailyPrice priceWithRange(LocalDate tradeDate, String close, String high, String low) {
        BigDecimal closeValue = new BigDecimal(close);
        BigDecimal highValue = new BigDecimal(high);
        BigDecimal lowValue = new BigDecimal(low);
        return new AdjustedDailyPrice(
            instrumentId, "TEST", tradeDate, closeValue, closeValue, highValue, highValue, lowValue, lowValue,
            BigDecimal.ONE, List.of()
        );
    }

    private String[] sixtyOnePricesStartingAt100() {
        String[] closes = new String[61];
        for (int i = 0; i < 61; i++) {
            closes[i] = String.valueOf(100 + i);
        }
        return closes;
    }
}
