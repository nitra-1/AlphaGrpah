package com.alphagraph.api.watchlist;

import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.api.WatchlistItem;
import com.alphagraph.decision.engine.DecisionScoreReader;
import com.alphagraph.decision.watchlist.WatchlistService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Assembles {@link WatchlistEntryDto}s by calling decision.watchlist.WatchlistService (owns the
 * watchlist itself) and decision.engine.DecisionScoreReader (owns the current signal) directly -
 * the same "api module assembles DTOs from domain modules' own readers" pattern
 * api.dashboard.DashboardService already established (docs/004_API_Architecture.md §5).
 */
@Service
public class WatchlistViewService {

    private final WatchlistService watchlistService;
    private final DecisionScoreReader decisionScoreReader;

    public WatchlistViewService(WatchlistService watchlistService, DecisionScoreReader decisionScoreReader) {
        this.watchlistService = watchlistService;
        this.decisionScoreReader = decisionScoreReader;
    }

    public List<WatchlistEntryDto> list() {
        return watchlistService.list().stream().map(this::toDto).toList();
    }

    public Optional<WatchlistEntryDto> add(UUID instrumentId) {
        return watchlistService.add(instrumentId).map(this::toDto);
    }

    public boolean remove(UUID instrumentId) {
        return watchlistService.remove(instrumentId);
    }

    private WatchlistEntryDto toDto(WatchlistItem item) {
        Optional<DecisionScore> score = decisionScoreReader.findLatest(item.instrumentId());
        return new WatchlistEntryDto(
            item.instrumentId(), item.symbol(), item.addedAt(),
            score.map(DecisionScore::swingScore).orElse(null),
            score.map(s -> s.swingRating().name()).orElse(null),
            score.map(DecisionScore::swingRank).orElse(null),
            score.map(DecisionScore::longTermScore).orElse(null),
            score.map(s -> s.longTermRating().name()).orElse(null),
            score.map(DecisionScore::longTermRank).orElse(null)
        );
    }
}
