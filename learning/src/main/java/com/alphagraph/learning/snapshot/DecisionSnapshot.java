package com.alphagraph.learning.snapshot;

import com.alphagraph.decision.api.DecisionRating;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One archived row from learning.decision_snapshots - deliberately its own type, not a re-read of decision.api.DecisionScore, since the archive is meant to stay stable even if decision_scores' own shape changes later. */
public record DecisionSnapshot(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    double swingScore, DecisionRating swingRating, Integer swingRank,
    double longTermScore, DecisionRating longTermRating, Integer longTermRank,
    Double technicalScore, Double fundamentalScore, Double institutionalScore,
    Double sectorScore, Double riskScore, Double corporateScore,
    double confidence, int ruleSetVersion, Instant decisionComputedAt, Instant capturedAt
) {
}
