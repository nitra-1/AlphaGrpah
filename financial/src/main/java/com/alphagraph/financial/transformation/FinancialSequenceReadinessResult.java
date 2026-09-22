package com.alphagraph.financial.transformation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Always computed, every real run, for every instrument with any real Financial Stage 1 evidence -
 * independent of whether any {@link FinancialSequenceResult} was also produced. This is what keeps
 * "no sequence row" honestly distinguishable between "genuinely nothing formed" ({@code READY}) and
 * "not enough real history to tell" ({@code INSUFFICIENT_HISTORY}). {@code historyPeriods} is the
 * real row count found in the fetched lookback window.
 */
record FinancialSequenceReadinessResult(UUID instrumentId, String symbol, LocalDate asOfDate, int historyPeriods, FinancialSequenceReadiness readiness) {
}
