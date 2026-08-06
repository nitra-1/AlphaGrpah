package com.alphagraph.api.rankings;

import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.engine.DecisionScoreReader;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankingsServiceTest {

    private final DecisionScoreReader decisionScoreReader = mock(DecisionScoreReader.class);
    private final RankingsService service = new RankingsService(decisionScoreReader);

    @Test
    void mapsEveryFieldFromTheLatestDecisionScore() {
        UUID instrumentId = UUID.randomUUID();
        DecisionScore score = new DecisionScore(
            instrumentId, "TCS", LocalDate.of(2026, 6, 1),
            85.16, DecisionRating.STRONG_BUY, 1,
            72.35, DecisionRating.BUY, 1,
            100.0, 85.0, 60.0, 100.0, 48.75, null,
            85.0, 1, Instant.now()
        );
        when(decisionScoreReader.findAllLatest()).thenReturn(List.of(score));

        RankingEntryDto dto = service.list().get(0);

        assertThat(dto.symbol()).isEqualTo("TCS");
        assertThat(dto.swingScore()).isEqualTo(85.16);
        assertThat(dto.swingRating()).isEqualTo("STRONG_BUY");
        assertThat(dto.swingRank()).isEqualTo(1);
        assertThat(dto.corporateScore()).isNull();
    }

    @Test
    void emptyWhenNoInstrumentHasBeenScoredYet() {
        when(decisionScoreReader.findAllLatest()).thenReturn(List.of());

        assertThat(service.list()).isEmpty();
    }
}
