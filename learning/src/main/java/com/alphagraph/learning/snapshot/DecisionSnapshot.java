package com.alphagraph.learning.snapshot;

import com.alphagraph.decision.api.DecisionRating;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One archived row from learning.decision_snapshots - deliberately its own type, not a re-read of
 * decision.api.DecisionScore, since the archive is meant to stay stable even if decision_scores'
 * own shape changes later. The provenance block (from {@code technicalScoreAsOfDate} through
 * {@code longTermRankUniverseSize}) mirrors decision.decision_scores' own Learning Readiness
 * Hardening columns - inserted before {@code capturedAt} so the archive timestamp stays the
 * trailing-most field, matching the original convention.
 */
public record DecisionSnapshot(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    double swingScore, DecisionRating swingRating, Integer swingRank,
    double longTermScore, DecisionRating longTermRating, Integer longTermRank,
    Double technicalScore, Double fundamentalScore, Double institutionalScore,
    Double sectorScore, Double riskScore, Double corporateScore,
    double confidence, int ruleSetVersion, Instant decisionComputedAt,
    LocalDate technicalScoreAsOfDate, Integer technicalRuleSetVersion, Instant technicalComputedAt,
    LocalDate fundamentalScoreAsOfDate, Integer fundamentalRuleSetVersion, Instant fundamentalComputedAt,
    LocalDate institutionalScoreAsOfDate, Integer institutionalRuleSetVersion, Instant institutionalComputedAt,
    LocalDate sectorScoreAsOfDate, Integer sectorRuleSetVersion, Instant sectorComputedAt,
    LocalDate riskScoreAsOfDate, Integer riskRuleSetVersion, Instant riskComputedAt,
    LocalDate corporateScoreAsOfDate, Integer corporateRuleSetVersion, Instant corporateComputedAt,
    UUID decisionRunId, Integer swingRankUniverseSize, Integer longTermRankUniverseSize,
    Instant capturedAt
) {
}
