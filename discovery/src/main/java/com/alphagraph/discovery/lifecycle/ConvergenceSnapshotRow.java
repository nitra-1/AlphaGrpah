package com.alphagraph.discovery.lifecycle;

import java.time.LocalDate;
import java.util.List;

/**
 * One real Stage 4 snapshot, read directly from {@code discovery.convergence_snapshots} - Stage
 * 5's own DTO over Stage 4's own output table (see {@link ConvergenceHistoryReader}'s javadoc for
 * why this reads raw SQL rather than importing {@code discovery.convergence.ConvergenceResult}).
 * Score fields are nullable exactly as Stage 4 persists them - {@code readiness ==
 * "INSUFFICIENT_DATA"} means every score field here is {@code null}, never a real zero.
 */
record ConvergenceSnapshotRow(
    LocalDate asOfDate, String convergenceState, String preContradictionState,
    Double convergenceScore, Double rawConvergenceScore, Double contradictionPenalty,
    int activeDomainCount, int domainCoverageCount,
    Double breadthScore, Double maturityScore, Double recencyScore, Double confidenceScore, Double densityScore,
    String readiness, LocalDate earliestSupportingDate, LocalDate latestSupportingDate,
    List<DomainContributionRow> domainContributions
) {
    boolean isReady() {
        return "READY".equals(readiness);
    }

    DomainContributionRow domain(String domainName) {
        return domainContributions.stream().filter(d -> d.domain().equals(domainName)).findFirst().orElse(null);
    }
}
