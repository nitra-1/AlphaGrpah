package com.alphagraph.discovery.convergence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One instrument's resolved convergence snapshot as of {@code asOfDate}. {@code
 * preContradictionState} is computed strictly before, and independent of,
 * {@code contradictionPenalty} (round-2 correction 2); {@code convergenceState} is the display
 * value, which only ever overlays {@code CONVERGENCE_WITH_CONTRADICTIONS} on top of an actual
 * positive {@code preContradictionState} (round-2 correction 1). All 8 score/penalty fields are
 * {@code null} when {@code readiness == INSUFFICIENT_DATA} - genuinely unevaluated, never a real
 * zero (round-2 correction 4); consumers must check {@code readiness} before interpreting any of
 * them. Always constructed, every real run, for every tracked instrument - distinct from each
 * source domain's own Stage 3 result, which is only constructed once a sequence has started
 * forming.
 */
record ConvergenceResult(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    ConvergenceState convergenceState, PreContradictionState preContradictionState,
    int activeDomainCount, int qualifyingSequenceCount,
    int domainCoverageCount, int domainCoveragePct,
    Double breadthScore, Double maturityScore, Double recencyScore, Double confidenceScore, Double densityScore,
    Double rawConvergenceScore, Double contradictionPenalty, Double convergenceScore,
    LocalDate earliestSupportingDate, LocalDate latestSupportingDate,
    ConvergenceReadiness readiness, int ruleVersion,
    List<DomainContribution> contributions, List<ReasonCode> reasons
) {
}
