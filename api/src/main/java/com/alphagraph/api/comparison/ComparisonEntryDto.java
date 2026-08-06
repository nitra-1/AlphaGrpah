package com.alphagraph.api.comparison;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One instrument's side-by-side row (Module 3.5) - every field mirrors what
 * decision.api.DecisionScore already carries, since Module 3.1 already blends all six domain
 * scores plus Swing/Long-Term Score into one record; this is purely a re-assembly, not a new
 * computation. All score fields are null when the instrument hasn't been scored yet at all
 * (asOfDate null too) rather than the instrument being silently dropped from the response.
 */
public record ComparisonEntryDto(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    Double technicalScore, Double fundamentalScore, Double institutionalScore,
    Double sectorScore, Double riskScore, Double corporateScore,
    Double swingScore, String swingRating, Integer swingRank,
    Double longTermScore, String longTermRating, Integer longTermRank,
    Double confidence
) {
}
