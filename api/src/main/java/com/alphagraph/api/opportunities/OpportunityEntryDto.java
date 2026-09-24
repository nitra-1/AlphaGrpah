package com.alphagraph.api.opportunities;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One instrument's current Stage 5 lifecycle classification - the Opportunities dashboard's own
 * landing view onto the Stage 1-5 discovery pipeline, analogous to {@code
 * api.rankings.RankingEntryDto}'s relationship to Module 3.1. {@code asOfDate} is always present
 * so the frontend can flag a stale result rather than let an old classification look current.
 */
public record OpportunityEntryDto(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    String lifecycleState, String lifecycleReadiness, String trajectoryDirection,
    Double lifecycleStrength, Double trajectoryScore, Double currentConvergenceScore,
    Integer currentActiveDomains, Integer lifecycleAgeDays
) {
}
