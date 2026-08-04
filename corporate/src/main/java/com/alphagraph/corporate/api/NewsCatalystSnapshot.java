package com.alphagraph.corporate.api;

import com.alphagraph.common.engine.Score;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Layer 2: {@code corporate.news.NewsCatalystEngine}'s point-in-time output for one instrument. */
public record NewsCatalystSnapshot(
    UUID instrumentId, String symbol, LocalDate asOfDate, double catalystScore, NewsCatalystTrend catalystTrend,
    int recentCatalystCount, double confidence, int ruleSetVersion, Instant computedAt
) implements Score {

    @Override
    public double value() {
        return catalystScore;
    }
}
