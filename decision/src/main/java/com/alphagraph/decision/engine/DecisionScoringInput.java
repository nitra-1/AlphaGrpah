package com.alphagraph.decision.engine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One instrument's inputs to the Scoring/Decision Engine - the point-in-time roll-up of all six
 * existing domain scores. Every score field is nullable - an instrument missing a given domain's
 * history simply doesn't contribute to that domain's weight, the same null-tolerant pattern
 * {@code corporate.signal.CorporateSignalInput} and {@code intelligence.risk.RiskAnalysisOrchestrator}
 * already established. Each domain's provenance triple (as-of date, rule-set version, computed-at)
 * travels alongside its score, unchanged from what {@code recomputeOne} resolved via
 * {@code findAsOf(instrumentId, asOfDate)} - carried straight through into {@link DecisionScoringEngine#calculate}.
 */
record DecisionScoringInput(
    UUID instrumentId, String symbol,
    Double technicalScore, Double fundamentalScore, Double institutionalScore,
    Double sectorScore, Double riskScore, Double corporateScore,
    LocalDate asOfDate,
    LocalDate technicalScoreAsOfDate, Integer technicalRuleSetVersion, Instant technicalComputedAt,
    LocalDate fundamentalScoreAsOfDate, Integer fundamentalRuleSetVersion, Instant fundamentalComputedAt,
    LocalDate institutionalScoreAsOfDate, Integer institutionalRuleSetVersion, Instant institutionalComputedAt,
    LocalDate sectorScoreAsOfDate, Integer sectorRuleSetVersion, Instant sectorComputedAt,
    LocalDate riskScoreAsOfDate, Integer riskRuleSetVersion, Instant riskComputedAt,
    LocalDate corporateScoreAsOfDate, Integer corporateRuleSetVersion, Instant corporateComputedAt,
    UUID decisionRunId
) {
}
