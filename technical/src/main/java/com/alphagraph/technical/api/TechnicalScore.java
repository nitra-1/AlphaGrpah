package com.alphagraph.technical.api;

import com.alphagraph.common.engine.Score;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The Technical Engine's output for one instrument on one date, mirroring
 * {@code technical.technical_scores} (docs/003_Database_Architecture.md §3a). Implements the
 * shared {@link Score} contract on a 0-100 scale (not 0-1) — chosen to match the roadmap's own
 * worked example ("Confidence 92%", "Market Behaviour Score 88") rather than the 0-1 fraction
 * {@code common.quality.DataQualityScore} uses; {@link Score} itself doesn't mandate either.
 * {@code value()} is {@code marketBehaviourScore}; {@code confidence()} is {@code trendConfidence}.
 * {@code sma200} and {@code stage} are nullable: both need ~200 real trading days of history,
 * more than the ~95 real days Module 1.5 backfilled.
 */
public record TechnicalScore(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    Trend trend, double trendConfidence, Momentum momentum, VolumeState volumeState,
    BreakoutStatus breakoutStatus, Integer stage, double marketBehaviourScore,
    Double sma20, Double sma50, Double sma200, Double weeklySma30,
    Double rsi14, Double macdLine, Double macdSignal, Double macdHistogram,
    Double adx14, Double atr14, Long obv, Double relativeVolume,
    int ruleSetVersion, Instant computedAt
) implements Score {

    @Override
    public double value() {
        return marketBehaviourScore;
    }

    @Override
    public double confidence() {
        return trendConfidence;
    }
}
