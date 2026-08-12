package com.alphagraph.api.learning;

public record RatingHitRateDto(String rating, int horizonDays, int sampleSize, Double hitRatePercentage, boolean sufficientData) {
}
