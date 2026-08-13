package com.alphagraph.learning.snapshot;

import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.engine.DecisionRun;
import com.alphagraph.decision.engine.DecisionRunReader;
import com.alphagraph.decision.engine.DecisionScoreReader;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionSnapshotOrchestratorTest {

    private static final double MIN_COVERAGE = 0.8;

    private final DecisionScoreReader decisionScoreReader = mock(DecisionScoreReader.class);
    private final DecisionSnapshotStore store = mock(DecisionSnapshotStore.class);
    private final DecisionRunReader runReader = mock(DecisionRunReader.class);
    private final DecisionSnapshotOrchestrator orchestrator =
        new DecisionSnapshotOrchestrator(decisionScoreReader, store, runReader, MIN_COVERAGE);

    @Test
    void archivesEveryScoreForTheGivenDateOnceTheRunIsCompletedWithSufficientCoverage() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        DecisionScore a = score("TCS", date);
        DecisionScore b = score("INFY", date);
        when(runReader.findByDate(date)).thenReturn(Optional.of(completedRun(date, 2, 2)));
        when(decisionScoreReader.findAllByDate(date)).thenReturn(List.of(a, b));

        orchestrator.archiveForDate(date);

        verify(store).archive(a);
        verify(store).archive(b);
        verify(store, times(2)).archive(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void throwsWhenNoRunExistsForTheDate() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        when(runReader.findByDate(date)).thenReturn(Optional.empty());

        assertThatIllegalStateException()
            .isThrownBy(() -> orchestrator.archiveForDate(date))
            .withMessageContaining("No decision run recorded");

        verify(store, times(0)).archive(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void throwsWhenTheRunIsNotCompleted() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        DecisionRun running = new DecisionRun(UUID.randomUUID(), date, Instant.now(), null, "RUNNING", null, null, 1);
        when(runReader.findByDate(date)).thenReturn(Optional.of(running));

        assertThatIllegalStateException()
            .isThrownBy(() -> orchestrator.archiveForDate(date))
            .withMessageContaining("RUNNING")
            .withMessageContaining("not COMPLETED");

        verify(store, times(0)).archive(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void throwsWhenTheCompletedRunHasInsufficientCoverage() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        // 3 of 500 ranked - well below the 80% minimum, even though the run itself completed.
        when(runReader.findByDate(date)).thenReturn(Optional.of(completedRun(date, 500, 3)));

        assertThatIllegalStateException()
            .isThrownBy(() -> orchestrator.archiveForDate(date))
            .withMessageContaining("insufficient coverage");

        verify(store, times(0)).archive(org.mockito.ArgumentMatchers.any());
    }

    private DecisionRun completedRun(LocalDate date, int instrumentCount, int rankedCount) {
        return new DecisionRun(UUID.randomUUID(), date, Instant.now(), Instant.now(), "COMPLETED", instrumentCount, rankedCount, 1);
    }

    private DecisionScore score(String symbol, LocalDate date) {
        return new DecisionScore(
            UUID.randomUUID(), symbol, date,
            60.0, DecisionRating.BUY, 1, 60.0, DecisionRating.BUY, 1,
            null, null, null, null, null, null,
            80.0, 1, Instant.now(),
            null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );
    }
}
