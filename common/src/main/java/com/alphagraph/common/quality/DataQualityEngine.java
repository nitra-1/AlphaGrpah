package com.alphagraph.common.quality;

/**
 * Turns a batch's raw counts into a {@link DataQualityScore}. The weighting below is a Phase 0
 * placeholder (equal weight across all four dimensions) — per docs/002_Engine_Architecture.md §3
 * the real formula is meant to become a Rule evaluated by the Rule Engine (Module 0.7) so it can
 * be tuned without a code change. Nothing here decides whether a score is "good enough" to let
 * downstream engines run on the batch — that gate belongs to the scheduler (Module 0.8).
 */
public final class DataQualityEngine {

    public DataQualityScore score(DataQualityInput input) {
        double completeness = input.completeness();
        double duplicateRate = input.duplicateRate();
        double missingFieldRate = input.missingFieldRate();
        double validationErrorRate = input.validationErrorRate();

        double score = (completeness
            + (1 - duplicateRate)
            + (1 - missingFieldRate)
            + (1 - validationErrorRate)) / 4.0;

        return new DataQualityScore(completeness, duplicateRate, missingFieldRate, validationErrorRate, score);
    }
}
