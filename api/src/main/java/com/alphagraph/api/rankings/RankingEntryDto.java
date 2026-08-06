package com.alphagraph.api.rankings;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One instrument's current Rank (Module 3.1's own output), listed for every tracked instrument -
 * the "what does AlphaGraph think right now" view, distinct from api.comparison's
 * ComparisonEntryDto (which only returns explicitly requested instrument ids). Same field shape
 * as ComparisonEntryDto since both mirror decision.api.DecisionScore, kept as a separate DTO type
 * per this project's per-feature-package convention rather than sharing one across packages.
 */
public record RankingEntryDto(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    Double technicalScore, Double fundamentalScore, Double institutionalScore,
    Double sectorScore, Double riskScore, Double corporateScore,
    Double swingScore, String swingRating, Integer swingRank,
    Double longTermScore, String longTermRating, Integer longTermRank,
    Double confidence
) {
}
