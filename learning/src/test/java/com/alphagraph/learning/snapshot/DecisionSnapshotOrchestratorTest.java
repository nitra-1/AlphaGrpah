package com.alphagraph.learning.snapshot;

import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.engine.DecisionScoreReader;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionSnapshotOrchestratorTest {

    private final DecisionScoreReader decisionScoreReader = mock(DecisionScoreReader.class);
    private final DecisionSnapshotStore store = mock(DecisionSnapshotStore.class);
    private final DecisionSnapshotOrchestrator orchestrator = new DecisionSnapshotOrchestrator(decisionScoreReader, store);

    @Test
    void archivesEveryScoreForTheGivenDate() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        DecisionScore a = score("TCS", date);
        DecisionScore b = score("INFY", date);
        when(decisionScoreReader.findAllByDate(date)).thenReturn(List.of(a, b));

        orchestrator.archiveForDate(date);

        verify(store).archive(a);
        verify(store).archive(b);
        verify(store, times(2)).archive(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void archivesNothingWhenNoScoresExistForTheDate() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        when(decisionScoreReader.findAllByDate(date)).thenReturn(List.of());

        orchestrator.archiveForDate(date);

        verify(store, times(0)).archive(org.mockito.ArgumentMatchers.any());
    }

    private DecisionScore score(String symbol, LocalDate date) {
        return new DecisionScore(
            UUID.randomUUID(), symbol, date,
            60.0, DecisionRating.BUY, 1, 60.0, DecisionRating.BUY, 1,
            null, null, null, null, null, null,
            80.0, 1, Instant.now()
        );
    }
}
