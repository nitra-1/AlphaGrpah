package com.alphagraph.decision.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * decision's own copy of one row from {@code discovery.lifecycle_snapshots} - {@code
 * discovery.lifecycle} is entirely package-private (confirmed by reading it directly), so this
 * mirrors its column shape rather than importing anything. {@code lifecycleState} is null exactly
 * when {@code lifecycleReadiness != READY} - never a fake DORMANT for thin history, the same
 * discipline the source table itself documents.
 */
public record LifecycleSnapshot(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    String lifecycleState, String lifecycleReadiness, String trajectoryDirection, String peakLifecycleStage,
    Double lifecycleStrength, Double trajectoryScore,
    Double currentConvergenceScore, Double peakConvergenceScore,
    Integer currentActiveDomains, Integer peakActiveDomains,
    LocalDate lifecycleStartedDate, LocalDate stateStartedDate, Integer lifecycleAgeDays,
    Instant computedAt
) {
}
