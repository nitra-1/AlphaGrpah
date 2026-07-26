package com.alphagraph.api.pipeline;

public record DataQualityScoreDto(
    double completeness, double duplicateRate, double missingFieldRate, double validationErrorRate, double score
) {
}
