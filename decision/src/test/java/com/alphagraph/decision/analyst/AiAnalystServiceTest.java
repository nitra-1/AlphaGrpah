package com.alphagraph.decision.analyst;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiAnalystServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AnalystEvidenceBuilder evidenceBuilder = mock(AnalystEvidenceBuilder.class);
    private final AiAnalystClient client = mock(AiAnalystClient.class);
    private final AnalystExplanationReader explanationReader = mock(AnalystExplanationReader.class);
    private final AnalystExplanationStore explanationStore = mock(AnalystExplanationStore.class);

    private final AiAnalystService service =
        new AiAnalystService(jdbcTemplate, evidenceBuilder, client, explanationReader, explanationStore);

    private final UUID instrumentId = UUID.randomUUID();

    @Test
    void scoreExplanationCacheHitSkipsEvidenceBuildingAndClaude() {
        when(explanationReader.find(eq(instrumentId), eq(AiAnalystService.SCORE_CHANGE), any(LocalDate.class)))
            .thenReturn(Optional.of("cached explanation"));

        String result = service.explainScoreChange(instrumentId);

        assertThat(result).isEqualTo("cached explanation");
        verifyNoInteractions(evidenceBuilder);
        verifyNoInteractions(client);
        verify(explanationStore, never()).write(any(), anyString(), any(), anyString());
        verifyNoInteractions(jdbcTemplate); // resolveSymbol never runs on a cache hit
    }

    @Test
    void scoreExplanationCacheMissBuildsEvidenceCallsClaudeAndCaches() {
        when(explanationReader.find(eq(instrumentId), eq(AiAnalystService.SCORE_CHANGE), any(LocalDate.class)))
            .thenReturn(Optional.empty());
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(instrumentId))).thenReturn("TCS");
        List<EvidenceFact> facts = List.of(new EvidenceFact("SCORE_CURRENT", "Corporate Score is currently 60.0 (NEUTRAL)"));
        when(evidenceBuilder.buildScoreEvidence(instrumentId)).thenReturn(facts);
        when(client.explain("TCS", facts)).thenReturn("fresh explanation");

        String result = service.explainScoreChange(instrumentId);

        assertThat(result).isEqualTo("fresh explanation");
        verify(explanationStore).write(eq(instrumentId), eq(AiAnalystService.SCORE_CHANGE), any(LocalDate.class), eq("fresh explanation"));
    }

    @Test
    void rankExplanationCacheHitSkipsEvidenceBuildingAndClaude() {
        when(explanationReader.find(eq(instrumentId), eq(AiAnalystService.RANK_CHANGE), any(LocalDate.class)))
            .thenReturn(Optional.of("cached rank explanation"));

        String result = service.explainRankChange(instrumentId);

        assertThat(result).isEqualTo("cached rank explanation");
        verifyNoInteractions(evidenceBuilder);
        verifyNoInteractions(client);
        verify(explanationStore, never()).write(any(), anyString(), any(), anyString());
    }

    @Test
    void rankExplanationCacheMissUsesRankEvidenceNotScoreEvidence() {
        when(explanationReader.find(eq(instrumentId), eq(AiAnalystService.RANK_CHANGE), any(LocalDate.class)))
            .thenReturn(Optional.empty());
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(instrumentId))).thenReturn("TCS");
        List<EvidenceFact> facts = List.of(new EvidenceFact("RANK_CURRENT", "Swing Rank is currently 1"));
        when(evidenceBuilder.buildRankEvidence(instrumentId)).thenReturn(facts);
        when(client.explain("TCS", facts)).thenReturn("fresh rank explanation");

        String result = service.explainRankChange(instrumentId);

        assertThat(result).isEqualTo("fresh rank explanation");
        verify(evidenceBuilder, never()).buildScoreEvidence(any());
        verify(explanationStore).write(eq(instrumentId), eq(AiAnalystService.RANK_CHANGE), any(LocalDate.class), eq("fresh rank explanation"));
    }
}
