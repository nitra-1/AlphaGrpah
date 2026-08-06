package com.alphagraph.decision.engine;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One instrument's inputs to the Scoring/Decision Engine - the point-in-time roll-up of all six
 * existing domain scores. Every score field is nullable - an instrument missing a given domain's
 * history simply doesn't contribute to that domain's weight, the same null-tolerant pattern
 * {@code corporate.signal.CorporateSignalInput} and {@code intelligence.risk.RiskAnalysisOrchestrator}
 * already established.
 */
record DecisionScoringInput(
    UUID instrumentId, String symbol,
    Double technicalScore, Double fundamentalScore, Double institutionalScore,
    Double sectorScore, Double riskScore, Double corporateScore,
    LocalDate asOfDate
) {
}
