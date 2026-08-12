package com.alphagraph.intelligence.priceadjustment;

import com.alphagraph.corporate.api.CorporateAction;
import com.alphagraph.market.api.DailyPrice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Back-adjusts raw historical closes for BONUS/SPLIT corporate actions, so a stock that did a
 * 1:1 bonus doesn't show a fabricated ~50% "crash" in raw close price on the ex-date - the gap
 * identified while scoping a backtest simulation (raw daily_prices has no split/bonus adjustment
 * anywhere in the pipeline; corporate_actions captures the ratio but nothing applies it).
 *
 * Deliberately stateless and on-demand rather than a persisted daily score: an adjustment factor
 * for a given historical date changes retroactively the moment a *later* BONUS/SPLIT action is
 * ingested, so persisting it once (like technical_scores/fundamental_scores are) would go stale
 * the next time new corporate-action data arrives. Recomputing on every read is cheap - it's a
 * handful of multiplications over a short action list, not a rule engine pass.
 *
 * Only BONUS and SPLIT are adjusted. DIVIDEND doesn't change share count so needs no price
 * adjustment. RIGHTS is deliberately excluded - it has both a ratio and a subscription price, and
 * the correct adjustment formula for rights issues is genuinely different (and less standardized)
 * from a straight bonus/split; treating it as a plain ratio would likely be wrong, so it's left
 * unadjusted rather than guessed. BUYBACK doesn't have a ratio field at all.
 */
@Component
public class PriceAdjustmentEngine {

    private static final int FACTOR_SCALE = 6;
    private static final int PRICE_SCALE = 2;

    public List<AdjustedDailyPrice> apply(List<DailyPrice> prices, List<CorporateAction> priceAffectingActions) {
        List<CorporateAction> sortedActions = priceAffectingActions.stream()
            .sorted(Comparator.comparing(CorporateAction::exDate))
            .toList();

        return prices.stream()
            .map(price -> adjust(price, sortedActions))
            .toList();
    }

    private AdjustedDailyPrice adjust(DailyPrice price, List<CorporateAction> sortedActions) {
        List<AppliedAdjustment> applied = sortedActions.stream()
            .filter(PriceAdjustmentEngine::isPriceAffecting)
            .filter(action -> action.exDate().isAfter(price.tradeDate()))
            .map(action -> new AppliedAdjustment(
                action.actionType(), action.exDate(), action.ratioNumerator(), action.ratioDenominator(), factorFor(action)
            ))
            .toList();

        BigDecimal cumulativeFactor = applied.stream()
            .map(AppliedAdjustment::factor)
            .reduce(BigDecimal.ONE, BigDecimal::multiply);

        BigDecimal adjustedClose = price.close().multiply(cumulativeFactor).setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        return new AdjustedDailyPrice(
            price.instrumentId(), price.symbol(), price.tradeDate(), price.close(), adjustedClose, cumulativeFactor, applied
        );
    }

    /** Filtered here, not just trusted from the caller - {@link #apply} is safe to call with an unfiltered action list, not only the BONUS/SPLIT-only list {@code CorporateActionsReader.findPriceAffectingActions} already returns. */
    private static boolean isPriceAffecting(CorporateAction action) {
        return "BONUS".equals(action.actionType()) || "SPLIT".equals(action.actionType());
    }

    /**
     * BONUS: ratioNumerator new shares issued for every ratioDenominator held (e.g. 1:1 issues one
     * bonus share per share held, doubling the holding). Post-action shares per pre-action share =
     * (denominator + numerator) / denominator, so a historical price scales down by the inverse:
     * denominator / (denominator + numerator).
     *
     * SPLIT: ratioNumerator new shares per ratioDenominator old shares (e.g. 5:1 turns one old
     * share into 5 new shares). Share count scales by numerator/denominator directly, so a
     * historical price scales down by the inverse: denominator / numerator.
     *
     * This is the standard NSE convention for how these ratios are reported, but it has never
     * been exercised against a real row - every BONUS/SPLIT sample and every live-fetched
     * corporate_actions row so far has been DIVIDEND-only (confirmed by checking both the bundled
     * sample CSV and the live database before writing this). Verify this formula against the
     * first real BONUS/SPLIT announcement the pipeline actually ingests, and correct it here if
     * NSE's real reported convention differs.
     */
    public BigDecimal factorFor(CorporateAction action) {
        if (action.ratioNumerator() == null || action.ratioDenominator() == null || action.ratioDenominator() == 0) {
            throw new IllegalStateException(
                "Cannot compute a price adjustment factor for " + action.symbol() + "'s " + action.actionType()
                    + " on " + action.exDate() + " - missing or zero ratio"
            );
        }
        BigDecimal numerator = BigDecimal.valueOf(action.ratioNumerator());
        BigDecimal denominator = BigDecimal.valueOf(action.ratioDenominator());

        return switch (action.actionType()) {
            case "BONUS" -> denominator.divide(denominator.add(numerator), FACTOR_SCALE, RoundingMode.HALF_UP);
            case "SPLIT" -> denominator.divide(numerator, FACTOR_SCALE, RoundingMode.HALF_UP);
            default -> throw new IllegalArgumentException("Not a price-affecting action type: " + action.actionType());
        };
    }
}
