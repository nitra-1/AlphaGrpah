package com.alphagraph.api.learning;

import java.util.List;

public record LearningEvidenceSummaryDto(
    int daysOfHistory, long decisionSnapshotCount, long forwardOutcomesComputed,
    List<RatingHitRateDto> swingRatingHitRates, List<RatingHitRateDto> longTermRatingHitRates
) {
}
