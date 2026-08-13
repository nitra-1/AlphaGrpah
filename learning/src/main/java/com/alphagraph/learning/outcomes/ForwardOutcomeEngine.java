package com.alphagraph.learning.outcomes;

import com.alphagraph.corporate.api.CorporateAction;
import com.alphagraph.intelligence.priceadjustment.AdjustedDailyPrice;
import com.alphagraph.intelligence.priceadjustment.PriceAdjustmentService;
import com.alphagraph.learning.snapshot.DecisionSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 4A.2/4.3 + Outcome Evidence Enrichment: measures what actually happened after a decision
 * snapshot, at 5/10/20/60 trading-day horizons - "trading days", counted as index offsets into the
 * adjusted price series, not calendar days, so weekends/holidays don't distort the horizon.
 * Horizons whose outcome date hasn't happened yet are simply not returned (never fabricated), and
 * horizons already present in {@code alreadyComputedHorizons} are skipped so a re-run only fills
 * gaps.
 *
 * <p>Thin orchestrator over three single-concern collaborators - {@link AbsoluteReturnCalculator}
 * (close-to-close return + directional correctness), {@link BenchmarkReturnCalculator}
 * (market/sector-relative return), {@link ExcursionCalculator} (MFE/MAE) - so none of them grows
 * into a monster as Phase 4 adds more outcome labels later. Uses split/bonus-adjusted closes
 * throughout (never raw {@code daily_prices}), so a bonus/split between the decision date and the
 * outcome date can't show up as a fabricated crash or spike.
 */
@Component
public class ForwardOutcomeEngine {

    private static final int[] HORIZONS = {5, 10, 20, 60};
    private static final int PRICE_SCALE = 2;

    private final AbsoluteReturnCalculator absoluteReturnCalculator;
    private final BenchmarkReturnCalculator benchmarkReturnCalculator;
    private final ExcursionCalculator excursionCalculator;
    private final PriceAdjustmentService priceAdjustmentService;

    public ForwardOutcomeEngine(
        AbsoluteReturnCalculator absoluteReturnCalculator, BenchmarkReturnCalculator benchmarkReturnCalculator,
        ExcursionCalculator excursionCalculator, PriceAdjustmentService priceAdjustmentService
    ) {
        this.absoluteReturnCalculator = absoluteReturnCalculator;
        this.benchmarkReturnCalculator = benchmarkReturnCalculator;
        this.excursionCalculator = excursionCalculator;
        this.priceAdjustmentService = priceAdjustmentService;
    }

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
        Instant priceAdjustmentWatermark = watermarkFor(snapshot.instrumentId());

        List<ForwardOutcome> outcomes = new ArrayList<>();
        for (int horizon : HORIZONS) {
            if (alreadyComputedHorizons.contains(horizon)) {
                continue;
            }
            int outcomeIndex = referenceIndex + horizon;
            if (outcomeIndex >= sorted.size()) {
                continue;
            }
            outcomes.add(buildOutcome(snapshot, sorted, referenceIndex, referencePrice, horizon, outcomeIndex, priceAdjustmentWatermark));
        }
        return outcomes;
    }

    /** Forces recomputation of exactly one already-computed horizon - used by ForwardOutcomeOrchestrator when a corporate action invalidates it. Delegates to {@link #computeOutcomes} with every horizon except the target one marked "already computed" so only it gets rebuilt. */
    public ForwardOutcome recomputeSingleOutcome(DecisionSnapshot snapshot, List<AdjustedDailyPrice> priceHistory, int horizonDays) {
        Set<Integer> allOtherHorizons = Arrays.stream(HORIZONS).boxed()
            .filter(h -> h != horizonDays)
            .collect(Collectors.toSet());
        List<ForwardOutcome> result = computeOutcomes(snapshot, priceHistory, allOtherHorizons);
        return result.isEmpty() ? null : result.get(0);
    }

    private ForwardOutcome buildOutcome(
        DecisionSnapshot snapshot, List<AdjustedDailyPrice> sorted, int referenceIndex, BigDecimal referencePrice,
        int horizon, int outcomeIndex, Instant priceAdjustmentWatermark
    ) {
        AdjustedDailyPrice outcome = sorted.get(outcomeIndex);
        BigDecimal outcomePrice = outcome.adjustedClose().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal forwardReturn = absoluteReturnCalculator.computeReturn(referencePrice, outcomePrice);

        BenchmarkReturnCalculator.BenchmarkResult market = benchmarkReturnCalculator.computeMarket(
            snapshot.asOfDate(), outcome.tradeDate(), forwardReturn
        );
        BenchmarkReturnCalculator.BenchmarkResult sector = benchmarkReturnCalculator.computeSector(
            snapshot.instrumentId(), snapshot.asOfDate(), outcome.tradeDate(), forwardReturn
        );
        ExcursionCalculator.ExcursionResult excursion = excursionCalculator.compute(sorted, referenceIndex, horizon, referencePrice);

        return new ForwardOutcome(
            snapshot.instrumentId(), snapshot.symbol(), snapshot.asOfDate(), horizon, outcome.tradeDate(),
            referencePrice, outcomePrice, forwardReturn,
            snapshot.swingRating(), absoluteReturnCalculator.directionallyCorrect(snapshot.swingRating(), forwardReturn),
            snapshot.longTermRating(), absoluteReturnCalculator.directionallyCorrect(snapshot.longTermRating(), forwardReturn),
            absoluteReturnCalculator.signalCorrect(snapshot.technicalScore(), forwardReturn),
            absoluteReturnCalculator.signalCorrect(snapshot.fundamentalScore(), forwardReturn),
            absoluteReturnCalculator.signalCorrect(snapshot.institutionalScore(), forwardReturn),
            absoluteReturnCalculator.signalCorrect(snapshot.sectorScore(), forwardReturn),
            absoluteReturnCalculator.signalCorrect(snapshot.riskScore(), forwardReturn),
            absoluteReturnCalculator.signalCorrect(snapshot.corporateScore(), forwardReturn),
            "CURRENT", priceAdjustmentWatermark, null,
            market.benchmarkInstrumentId(), market.returnPercentage(), market.outcomeDate(), market.excessReturnPercentage(),
            sector.benchmarkInstrumentId(), sector.returnPercentage(), sector.outcomeDate(), sector.excessReturnPercentage(),
            excursion == null ? null : excursion.mfePercentage(), excursion == null ? null : excursion.maePercentage()
        );
    }

    /** The newest ingestion timestamp among this instrument's real BONUS/SPLIT actions at computation time - lets ForwardOutcomeInvalidator later detect a later-ingested action that changes the adjusted price basis this outcome was computed against. Null if none exist yet. */
    private Instant watermarkFor(UUID instrumentId) {
        return priceAdjustmentService.findPriceAffectingActions(instrumentId).stream()
            .map(CorporateAction::createdAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
    }

    private static int indexOfDate(List<AdjustedDailyPrice> sorted, LocalDate date) {
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).tradeDate().equals(date)) {
                return i;
            }
        }
        return -1;
    }
}
