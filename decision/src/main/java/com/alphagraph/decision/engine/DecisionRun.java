package com.alphagraph.decision.engine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One decision-scoring pass for one day. {@code status} is {@code COMPLETED} once the whole
 * {@code DecisionScoringOrchestrator.recomputeAllInstruments()} call - including the ranking pass
 * - has finished, regardless of individual per-instrument failures already tolerated elsewhere
 * (those show up as {@code rankedCount < instrumentCount}, not as a non-COMPLETED status). Public,
 * same as {@code DecisionScoreReader}: {@code learning.snapshot.DecisionSnapshotOrchestrator}
 * reads this cross-module to gate archival on run completion and coverage.
 */
public record DecisionRun(
    UUID id, LocalDate asOfDate, Instant startedAt, Instant completedAt,
    String status, Integer instrumentCount, Integer rankedCount, int ruleSetVersion
) {
}
