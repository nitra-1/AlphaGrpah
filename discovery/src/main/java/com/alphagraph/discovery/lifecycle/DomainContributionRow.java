package com.alphagraph.discovery.lifecycle;

/**
 * One domain's contribution on a Stage 4 snapshot, read directly from {@code
 * discovery.convergence_domain_contributions} - Stage 5's own copy/DTO, never importing {@code
 * discovery.convergence}'s package-private {@code DomainContribution} (can't - different package,
 * same module). {@code evidenceReference} is the representative sequence's own {@code
 * sequence_type} (post Stage-4-bug-fix) - a best-effort v1 signal, not an exhaustive list of every
 * sequence active in that domain.
 */
record DomainContributionRow(
    String domain, String contributionStatus, String strongestPhase,
    Double domainStrength, Double domainConfidence, Double contributionScore, String evidenceReference
) {
    boolean isActive() {
        return "ACTIVE".equals(contributionStatus);
    }
}
