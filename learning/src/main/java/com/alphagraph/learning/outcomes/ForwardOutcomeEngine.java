package com.alphagraph.learning.outcomes;

import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.intelligence.priceadjustment.AdjustedDailyPrice;
import com.alphagraph.learning.snapshot.DecisionSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Phase 4A.2/4.3: measures what actually happened after a decision snapshot, at 5/10/20/60
 * trading-day horizons - "trading days", counted as index offsets into the adjusted price
 * series, not calendar days, so weekends/holidays don't distort the horizon. Horizons whose
 * outcome date hasn't happened yet are simply not returned (never fabricated), and horizons
 * already present in {@code alreadyComputedHorizons} are skipped so a re-run only fills gaps.
 *
 * Uses split/bonus-adjusted closes throughout (never raw {@code daily_prices}), so a bonus/split
 * between the decision date and the outcome date can't show up as a fabricated crash or spike.
 */
@Component
public class ForwardOutcomeEngine {

    private static final int[] HORIZONS = {5, 10, 20, 60};
    private static final int PRICE_SCALE = 2;
    private static final int RETURN_SCALE = 2;

    public List<ForwardOutcome> computeOutcomes(
        DecisionSnapshot snapshot, List<AdjustedDailyPrice> priceHistory, Set<Integer> alreadyComputedHorizons
    ) {
        List<AdjustedDailyPrice> sorted = priceHistory.stream()
            .sorted(Comparator.comparing(AdjustedDailyPrice::tradeDate))
            .toList();

        int referenceIndex = indexOfDate(sorted, snapshot.asOfDate());
        if (referenceIndex < 0) {
            return List.of();
        }

        BigDecimal referencePrice = sorted.get(referenceIndex).adjustedClose().setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        List<ForwardOutcome> outcomes = new ArrayList<>();
        for (int horizon : HORIZONS) {
            if (alreadyComputedHorizons.contains(horizon)) {
                continue;
            }
            int outcomeIndex = referenceIndex + horizon;
            if (outcomeIndex >= sorted.size()) {
                continue;
            }

            AdjustedDailyPrice outcome = sorted.get(outcomeIndex);
            BigDecimal outcomePrice = outcome.adjustedClose().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
            BigDecimal forwardReturn = outcomePrice.subtract(referencePrice)
                .divide(referencePrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(RETURN_SCALE, RoundingMode.HALF_UP);

            outcomes.add(new ForwardOutcome(
                snapshot.instrumentId(), snapshot.symbol(), snapshot.asOfDate(), horizon, outcome.tradeDate(),
                referencePrice, outcomePrice, forwardReturn,
                snapshot.swingRating(), directionallyCorrect(snapshot.swingRating(), forwardReturn),
                snapshot.longTermRating(), directionallyCorrect(snapshot.longTermRating(), forwardReturn),
                signalCorrect(snapshot.technicalScore(), forwardReturn),
                signalCorrect(snapshot.fundamentalScore(), forwardReturn),
                signalCorrect(snapshot.institutionalScore(), forwardReturn),
                signalCorrect(snapshot.sectorScore(), forwardReturn),
                signalCorrect(snapshot.riskScore(), forwardReturn),
                signalCorrect(snapshot.corporateScore(), forwardReturn)
            ));
        }
        return outcomes;
    }

    private static int indexOfDate(List<AdjustedDailyPrice> sorted, LocalDate date) {
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).tradeDate().equals(date)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * STRONG_BUY/BUY are bullish - correct if the forward return is positive. REDUCE/AVOID are
     * bearish - correct if the forward return is zero or negative. HOLD is deliberately excluded
     * (null): a neutral rating has no honest "correct direction" to score against.
     */
    private static Boolean directionallyCorrect(DecisionRating rating, BigDecimal forwardReturn) {
        boolean positive = forwardReturn.signum() > 0;
        return switch (rating) {
            case STRONG_BUY, BUY -> positive;
            case REDUCE, AVOID -> !positive;
            case HOLD -> null;
        };
    }

    /**
     * A domain score above 50 is treated as a bullish signal, below 50 as bearish - the standard
     * midpoint every domain engine already scores against (0-100, 50 neutral). A score of exactly
     * 50, or a missing score, is null (not applicable), never an arbitrary guess. This convention
     * hasn't been checked against real outcome data yet - revisit once enough forward_outcomes
     * rows exist to see whether 50 is actually the right split point for each domain.
     */
    private static Boolean signalCorrect(Double domainScore, BigDecimal forwardReturn) {
        if (domainScore == null || domainScore == 50.0) {
            return null;
        }
        boolean positive = forwardReturn.signum() > 0;
        return domainScore > 50.0 ? positive : !positive;
    }
}
