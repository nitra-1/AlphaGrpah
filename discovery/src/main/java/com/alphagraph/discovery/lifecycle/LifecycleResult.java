package com.alphagraph.discovery.lifecycle;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One instrument's resolved lifecycle classification as of {@code asOfDate}. {@code
 * lifecycleState} is {@code null} whenever {@code readiness != READY} - never a fake {@code
 * DORMANT} for thin history. Uses {@code Double}, not {@code BigDecimal}, for every score field -
 * a deliberate deviation from the literal spec, matching {@code discovery.convergence.
 * ConvergenceResult}'s own real convention exactly (Stage 4 never uses {@code BigDecimal}
 * anywhere; boxing only happens at the JDBC write boundary). {@code peakLifecycleStage} is one
 * field beyond the original spec's own list (added during plan review) - the running cycle-peak
 * state, persisted every snapshot so the next run's {@code previousLifecycle} lookup carries it
 * forward without re-scanning history; see {@link DiscoveryLifecycleEngine}'s javadoc.
 */
record LifecycleResult(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    LifecycleState lifecycleState, LifecycleReadiness readiness,
    LocalDate lifecycleStartedDate, LocalDate stateStartedDate,
    Integer lifecycleAgeDays,
    LifecycleState peakLifecycleStage,
    Double lifecycleStrength, Double trajectoryScore,
    Double currentConvergenceScore, Double peakConvergenceScore,
    Integer currentActiveDomains, Integer peakActiveDomains,
    TrajectoryDirection trajectoryDirection,
    int ruleVersion, List<LifecycleReason> reasons
) {
}
