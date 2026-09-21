package com.alphagraph.market.transformation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Always computed, every real day, for every instrument with any real Market Stage 2 history -
 * independent of whether any {@link MarketSequenceResult} was also produced that day. This is what
 * keeps "no sequence row" honestly distinguishable between "genuinely nothing formed" ({@code READY})
 * and "not enough real history to tell" ({@code INSUFFICIENT_HISTORY}) - see
 * {@code MarketSequenceReadiness}'s own javadoc.
 */
record MarketSequenceReadinessResult(UUID instrumentId, String symbol, LocalDate asOfDate, int historySessions, MarketSequenceReadiness readiness) {
}
