package com.alphagraph.decision.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** decision's own copy of one row from {@code discovery.convergence_snapshots}, plus its per-domain contributions. */
public record ConvergenceSnapshot(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    String convergenceState, String preContradictionState,
    int activeDomainCount, int domainCoverageCount, int domainCoveragePct,
    Double convergenceScore, String readiness,
    List<DomainContribution> domainContributions
) {
}
