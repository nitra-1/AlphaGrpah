package com.alphagraph.corporate.transformation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One metric's computed rolling-count change/persistence for one instrument as of one day.
 * Unlike {@code ownership}/{@code market}/{@code financial}'s evidence observations, there is no
 * "first observation, null prior" case - a trailing-180-day event count is always a real, whole
 * number (possibly zero), never a missing one.
 */
record CapitalAllocationEvidenceObservation(
    CapitalAllocationMetric metric, UUID instrumentId, String symbol, LocalDate asOfDate, LocalDate priorAsOfDate,
    int value, int priorValue, int change, int velocityPerDay, int persistenceDays, double confidence, int windowDays
) {
}
