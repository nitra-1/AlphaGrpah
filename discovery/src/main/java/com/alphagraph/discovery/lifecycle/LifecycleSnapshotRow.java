package com.alphagraph.discovery.lifecycle;

import java.time.LocalDate;

/** A previously-persisted, authoritative ({@code readiness=READY}, {@code lifecycle_state} non-null) Stage 5 snapshot - see {@link LifecycleSnapshotReader#findLatestAuthoritativeBefore}. */
record LifecycleSnapshotRow(
    LocalDate asOfDate, LifecycleState lifecycleState, TrajectoryDirection trajectoryDirection,
    LifecycleState peakLifecycleStage,
    LocalDate lifecycleStartedDate, LocalDate stateStartedDate,
    Double currentConvergenceScore, Double peakConvergenceScore,
    Integer currentActiveDomains, Integer peakActiveDomains
) {
}
