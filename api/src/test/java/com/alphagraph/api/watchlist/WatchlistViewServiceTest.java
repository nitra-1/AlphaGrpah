package com.alphagraph.api.watchlist;

import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.api.WatchlistItem;
import com.alphagraph.decision.engine.DecisionScoreReader;
import com.alphagraph.decision.watchlist.WatchlistService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WatchlistViewServiceTest {

    private final WatchlistService watchlistService = mock(WatchlistService.class);
    private final DecisionScoreReader decisionScoreReader = mock(DecisionScoreReader.class);
    private final WatchlistViewService viewService = new WatchlistViewService(watchlistService, decisionScoreReader);

    private final UUID userId = UUID.randomUUID();

    @Test
    void listEnrichesEveryItemWithItsLatestScore() {
        UUID instrumentId = UUID.randomUUID();
        WatchlistItem item = new WatchlistItem(UUID.randomUUID(), instrumentId, "TCS", Instant.parse("2026-06-01T00:00:00Z"));
        when(watchlistService.list(userId)).thenReturn(List.of(item));
        when(decisionScoreReader.findLatest(instrumentId)).thenReturn(Optional.of(scoreFor(instrumentId)));

        WatchlistEntryDto dto = viewService.list(userId).get(0);

        assertThat(dto.symbol()).isEqualTo("TCS");
        assertThat(dto.swingScore()).isEqualTo(85.16);
        assertThat(dto.swingRating()).isEqualTo("STRONG_BUY");
        assertThat(dto.swingRank()).isEqualTo(1);
        assertThat(dto.longTermScore()).isEqualTo(72.35);
        assertThat(dto.longTermRating()).isEqualTo("BUY");
        assertThat(dto.longTermRank()).isEqualTo(1);
    }

    @Test
    void listLeavesScoreFieldsNullForAnInstrumentNotYetScored() {
        UUID instrumentId = UUID.randomUUID();
        WatchlistItem item = new WatchlistItem(UUID.randomUUID(), instrumentId, "NEWCO", Instant.now());
        when(watchlistService.list(userId)).thenReturn(List.of(item));
        when(decisionScoreReader.findLatest(instrumentId)).thenReturn(Optional.empty());

        WatchlistEntryDto dto = viewService.list(userId).get(0);

        assertThat(dto.swingScore()).isNull();
        assertThat(dto.swingRating()).isNull();
        assertThat(dto.swingRank()).isNull();
    }

    @Test
    void addDelegatesToTheDomainServiceAndEnrichesTheResult() {
        UUID instrumentId = UUID.randomUUID();
        WatchlistItem item = new WatchlistItem(UUID.randomUUID(), instrumentId, "INFY", Instant.now());
        when(watchlistService.add(userId, instrumentId)).thenReturn(Optional.of(item));
        when(decisionScoreReader.findLatest(instrumentId)).thenReturn(Optional.empty());

        Optional<WatchlistEntryDto> result = viewService.add(userId, instrumentId);

        assertThat(result).isPresent();
        assertThat(result.get().symbol()).isEqualTo("INFY");
    }

    @Test
    void addReturnsEmptyWhenTheDomainServiceCouldNotResolveTheInstrument() {
        UUID instrumentId = UUID.randomUUID();
        when(watchlistService.add(userId, instrumentId)).thenReturn(Optional.empty());

        assertThat(viewService.add(userId, instrumentId)).isEmpty();
    }

    @Test
    void removeDelegatesDirectlyToTheDomainService() {
        UUID instrumentId = UUID.randomUUID();
        when(watchlistService.remove(userId, instrumentId)).thenReturn(true);

        assertThat(viewService.remove(userId, instrumentId)).isTrue();
    }

    private static DecisionScore scoreFor(UUID instrumentId) {
        return new DecisionScore(
            instrumentId, "TCS", LocalDate.of(2026, 6, 1),
            85.16, DecisionRating.STRONG_BUY, 1,
            72.35, DecisionRating.BUY, 1,
            100.0, 85.0, 60.0, 100.0, 48.75, null,
            85.0, 1, Instant.now()
        );
    }
}
