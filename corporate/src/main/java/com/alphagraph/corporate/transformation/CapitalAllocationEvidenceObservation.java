package com.alphagraph.corporate.transformation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One metric's computed rolling-count change/persistence for one instrument as of one day.
 * Unlike {@code ownership}/{@code market}/{@code financial}'s evidence observations, there is no
 * "first observation, null prior" case - a trailing-180-day event count is always a real, whole
 * number (possibly zero), never a missing one.
 *
 * <p>{@code enteredEventCount}/{@code exitedEventCount} are the true gross counts behind
 * {@code change} - {@code enteredEventCount} is the number of qualifying actions whose
 * {@code exDate} is exactly {@code asOfDate} (entering the rolling window today);
 * {@code exitedEventCount} is the number whose {@code exDate} is exactly {@code asOfDate -
 * windowDays} (aging out of the window today). {@code change} is always their net,
 * {@code enteredEventCount - exitedEventCount} - unlike {@code change}, the two gross counts never
 * cancel each other out, so a consumer that needs to know "did a real event happen today" should
 * read {@code enteredEventCount}, not infer it from {@code change > 0}.
 */
record CapitalAllocationEvidenceObservation(
    CapitalAllocationMetric metric, UUID instrumentId, String symbol, LocalDate asOfDate, LocalDate priorAsOfDate,
    int value, int priorValue, int change, int enteredEventCount, int exitedEventCount,
    int velocityPerDay, int persistenceDays, double confidence, int windowDays
) {
    CapitalAllocationEvidenceObservation {
        if (change != enteredEventCount - exitedEventCount) {
            throw new IllegalArgumentException(
                "change (" + change + ") must equal enteredEventCount (" + enteredEventCount
                    + ") - exitedEventCount (" + exitedEventCount + ")"
            );
        }
        if (enteredEventCount < 0 || exitedEventCount < 0) {
            throw new IllegalArgumentException(
                "enteredEventCount/exitedEventCount must never be negative: " + enteredEventCount + "/" + exitedEventCount
            );
        }
    }
}
