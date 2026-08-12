package com.alphagraph.learning.performance;

import com.alphagraph.decision.api.DecisionRating;

/**
 * Hit rate for one (rating, horizon) bucket - {@code hitRatePercentage} is {@code null} whenever
 * {@code sampleSize} is below the minimum ({@link ModelPerformanceReader#MIN_SAMPLE_SIZE}), never
 * a misleading 100%/0% computed from a handful of outcomes.
 */
public record RatingHitRate(
    DecisionRating rating, int horizonDays, int sampleSize, Double hitRatePercentage, boolean sufficientData
) {
}
