package com.alphagraph.corporate.transformation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Always computed, every real run, for every instrument with any real Capital Allocation Stage 1
 * evidence - independent of whether any {@link CapitalAllocationSequenceResult} was also
 * produced. {@code historyDays} is real calendar-day coverage (latest evidence date minus
 * earliest evidence date) of the least-observed metric that actually has evidence, not an
 * evidence-row count - see {@code CapitalAllocationTransformationSequenceEngine.evaluateReadiness}
 * for why row count would be a misleading readiness signal for this domain.
 */
record CapitalAllocationSequenceReadinessResult(UUID instrumentId, String symbol, LocalDate asOfDate, int historyDays, CapitalAllocationSequenceReadiness readiness) {
}
