package com.alphagraph.api.comparison;

import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.engine.DecisionScoreReader;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComparisonServiceTest {

    private final DecisionScoreReader decisionScoreReader = mock(DecisionScoreReader.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ComparisonService service = new ComparisonService(decisionScoreReader, jdbcTemplate);

    @Test
    void scoredInstrumentsCarryEveryDomainScoreAndTheSwingLongTermFields() {
        UUID id = UUID.randomUUID();
        when(decisionScoreReader.findLatest(id)).thenReturn(Optional.of(scoreOf(id, "TCS", 1)));

        ComparisonEntryDto dto = service.compare(List.of(id)).get(0);

        assertThat(dto.symbol()).isEqualTo("TCS");
        assertThat(dto.technicalScore()).isEqualTo(100.0);
        assertThat(dto.fundamentalScore()).isEqualTo(85.0);
        assertThat(dto.swingScore()).isEqualTo(85.16);
        assertThat(dto.swingRating()).isEqualTo("STRONG_BUY");
        assertThat(dto.swingRank()).isEqualTo(1);
    }

    @Test
    void anInstrumentWithNoScoreYetIsIncludedWithNullFieldsAndItsRealSymbol() {
        UUID id = UUID.randomUUID();
        when(decisionScoreReader.findLatest(id)).thenReturn(Optional.empty());
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class), eq(id))).thenReturn("NEWCO");

        ComparisonEntryDto dto = service.compare(List.of(id)).get(0);

        assertThat(dto.symbol()).isEqualTo("NEWCO");
        assertThat(dto.asOfDate()).isNull();
        assertThat(dto.technicalScore()).isNull();
        assertThat(dto.swingScore()).isNull();
        assertThat(dto.swingRank()).isNull();
    }

    @Test
    void anInstrumentIdThatDoesNotExistAtAllIsSilentlySkipped() {
        UUID unknownId = UUID.randomUUID();
        when(decisionScoreReader.findLatest(unknownId)).thenReturn(Optional.empty());
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class), eq(unknownId)))
            .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(service.compare(List.of(unknownId))).isEmpty();
    }

    @Test
    void resultsAreSortedBySwingRankAscendingWithNullsLast() {
        UUID rank3 = UUID.randomUUID();
        UUID rank1 = UUID.randomUUID();
        UUID unscored = UUID.randomUUID();
        when(decisionScoreReader.findLatest(rank3)).thenReturn(Optional.of(scoreOf(rank3, "C", 3)));
        when(decisionScoreReader.findLatest(rank1)).thenReturn(Optional.of(scoreOf(rank1, "A", 1)));
        when(decisionScoreReader.findLatest(unscored)).thenReturn(Optional.empty());
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class), eq(unscored))).thenReturn("B");

        List<ComparisonEntryDto> result = service.compare(List.of(rank3, rank1, unscored));

        assertThat(result).extracting(ComparisonEntryDto::symbol).containsExactly("A", "C", "B");
    }

    @Test
    void anEmptyRequestReturnsAnEmptyList() {
        assertThat(service.compare(List.of())).isEmpty();
    }

    private DecisionScore scoreOf(UUID instrumentId, String symbol, int swingRank) {
        return new DecisionScore(
            instrumentId, symbol, LocalDate.of(2026, 6, 1),
            85.16, DecisionRating.STRONG_BUY, swingRank,
            72.35, DecisionRating.BUY, 1,
            100.0, 85.0, 60.0, 100.0, 48.75, null,
            85.0, 1, Instant.now()
        );
    }
}
