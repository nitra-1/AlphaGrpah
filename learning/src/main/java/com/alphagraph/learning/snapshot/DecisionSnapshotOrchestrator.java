package com.alphagraph.learning.snapshot;

import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.engine.DecisionRun;
import com.alphagraph.decision.engine.DecisionRunReader;
import com.alphagraph.decision.engine.DecisionScoreReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Phase 4A.1: Decision Snapshot Archive. Copies one day's decision.decision_scores cohort into
 * learning.decision_snapshots, permanently. {@link #archiveForDate} takes an explicit date so it
 * can be called both by the daily scheduler (for "today") and, once, manually during rollout to
 * backfill the handful of days decision_scores already had before this feature existed - that
 * backfill is a real limitation, not a true same-day capture, and is disclosed as such wherever
 * it's run.
 *
 * <p>Learning Readiness Hardening: archival is gated on {@link DecisionRunReader}, not on trusting
 * that the 5-minute stagger between DecisionScoringScheduler (21:45 IST) and this class's own
 * scheduler (21:50 IST) was always enough. Two independent checks: (1) a decision.decision_runs
 * row for the date exists and is COMPLETED - a run still RUNNING or FAILED means archiving now
 * would capture a partial or stale batch; (2) even a COMPLETED run's coverage (rankedCount /
 * instrumentCount) must meet {@link #minRunCoverageRatio} - a run that "completed" after ranking
 * only 3 of 500 candidates isn't archival-ready either. Both failures throw a clear, distinct
 * message rather than silently skipping or archiving a thin/partial batch.
 */
@Component
public class DecisionSnapshotOrchestrator {

    private final DecisionScoreReader decisionScoreReader;
    private final DecisionSnapshotStore store;
    private final DecisionRunReader runReader;
    private final double minRunCoverageRatio;

    public DecisionSnapshotOrchestrator(
        DecisionScoreReader decisionScoreReader, DecisionSnapshotStore store, DecisionRunReader runReader,
        @Value("${alphagraph.learning.min-run-coverage-ratio:0.8}") double minRunCoverageRatio
    ) {
        this.decisionScoreReader = decisionScoreReader;
        this.store = store;
        this.runReader = runReader;
        this.minRunCoverageRatio = minRunCoverageRatio;
    }

    public void archiveForDate(LocalDate date) {
        DecisionRun run = requireArchivalReadyRun(date);

        List<DecisionScore> cohort = decisionScoreReader.findAllByDate(date);
        for (DecisionScore score : cohort) {
            store.archive(score);
        }
    }

    private DecisionRun requireArchivalReadyRun(LocalDate date) {
        Optional<DecisionRun> run = runReader.findByDate(date);
        if (run.isEmpty()) {
            throw new IllegalStateException("No decision run recorded for " + date + " - nothing to archive yet");
        }
        DecisionRun decisionRun = run.get();
        if (!"COMPLETED".equals(decisionRun.status())) {
            throw new IllegalStateException(
                "Decision run for " + date + " is " + decisionRun.status() + ", not COMPLETED - refusing to archive a partial batch"
            );
        }
        int instrumentCount = decisionRun.instrumentCount() == null ? 0 : decisionRun.instrumentCount();
        int rankedCount = decisionRun.rankedCount() == null ? 0 : decisionRun.rankedCount();
        double coverage = instrumentCount == 0 ? 0.0 : (double) rankedCount / instrumentCount;
        if (coverage < minRunCoverageRatio) {
            throw new IllegalStateException(
                "Decision run for " + date + " completed with insufficient coverage: " + rankedCount + "/" + instrumentCount
                    + " ranked (" + String.format("%.1f%%", coverage * 100) + "), minimum required "
                    + String.format("%.0f%%", minRunCoverageRatio * 100)
            );
        }
        return decisionRun;
    }
}
