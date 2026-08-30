package com.alphagraph.market.pricing;

import java.time.LocalDate;

/**
 * One symbol's backfill requirement - {@code targetBeforeDate} is the date {@link
 * MarketPriceBackfillOrchestrator} must accumulate {@value MarketPriceBackfillOrchestrator#TARGET_ROWS}
 * real trading sessions strictly *before*, not just any 20 total. Resolved by
 * {@link BackfillCandidateReader} from the symbol's own earliest unscored deal - never a flat
 * "today" for every symbol, since a freshly-discovered symbol's first deal is very often dated
 * exactly the day backfill's own most-recently-captured session lands on.
 */
record BackfillTarget(String symbol, LocalDate targetBeforeDate) {
}
