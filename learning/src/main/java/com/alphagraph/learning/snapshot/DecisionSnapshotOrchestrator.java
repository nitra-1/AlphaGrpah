package com.alphagraph.learning.snapshot;

import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.engine.DecisionScoreReader;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Phase 4A.1: Decision Snapshot Archive. Copies one day's decision.decision_scores cohort into
 * learning.decision_snapshots, permanently. {@link #archiveForDate} takes an explicit date so it
 * can be called both by the daily scheduler (for "today") and, once, manually during rollout to
 * backfill the handful of days decision_scores already had before this feature existed - that
 * backfill is a real limitation, not a true same-day capture, and is disclosed as such wherever
 * it's run.
 */
@Component
public class DecisionSnapshotOrchestrator {

    private final DecisionScoreReader decisionScoreReader;
    private final DecisionSnapshotStore store;

    public DecisionSnapshotOrchestrator(DecisionScoreReader decisionScoreReader, DecisionSnapshotStore store) {
        this.decisionScoreReader = decisionScoreReader;
        this.store = store;
    }

    public void archiveForDate(LocalDate date) {
        List<DecisionScore> cohort = decisionScoreReader.findAllByDate(date);
        for (DecisionScore score : cohort) {
            store.archive(score);
        }
    }
}
