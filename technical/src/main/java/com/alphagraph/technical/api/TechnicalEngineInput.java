package com.alphagraph.technical.api;

import java.util.List;
import java.util.UUID;

/**
 * The Technical Engine's input for one instrument: its daily bar history, ordered ascending by
 * {@link DailyBar#tradeDate()}. Per docs/002_Engine_Architecture.md §5, this is already-normalized
 * data assembled by {@code intelligence} from market's published API — the engine itself never
 * fetches anything.
 */
public record TechnicalEngineInput(UUID instrumentId, String symbol, List<DailyBar> dailyBars) {

    public TechnicalEngineInput {
        dailyBars = List.copyOf(dailyBars);
    }
}
