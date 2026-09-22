package com.alphagraph.ownership.transformation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Always computed, every real run, for every instrument with any real Ownership Stage 1 evidence -
 * independent of whether any {@link OwnershipSequenceResult} was also produced. This is what keeps
 * "no sequence row" honestly distinguishable between "genuinely nothing formed" ({@code READY}) and
 * "not enough real history to tell" ({@code INSUFFICIENT_HISTORY}). {@code historyPeriods} (not
 * "sessions") is the real distinct-quarter count found in the canonicalized lookback window.
 */
record OwnershipSequenceReadinessResult(UUID instrumentId, String symbol, LocalDate asOfDate, int historyPeriods, OwnershipSequenceReadiness readiness) {
}
