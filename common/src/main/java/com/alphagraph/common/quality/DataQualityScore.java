package com.alphagraph.common.quality;

/**
 * Mirrors {@code common.data_quality_scores} (docs/003_Database_Architecture.md §3) — every
 * field here is a rate in [0, 1], matching that table's CHECK constraints.
 */
public record DataQualityScore(
    double completeness,
    double duplicateRate,
    double missingFieldRate,
    double validationErrorRate,
    double score
) {
}
