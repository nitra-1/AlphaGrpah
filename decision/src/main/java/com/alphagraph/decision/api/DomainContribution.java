package com.alphagraph.decision.api;

/** decision's own copy of one row from {@code discovery.convergence_domain_contributions} - one of the 5 positive domains' contribution to a convergence snapshot. */
public record DomainContribution(
    String domain, String contributionStatus, int activeSequenceCount, String strongestPhase,
    Double domainStrength, Double domainConfidence, Double contributionScore, String evidenceReference
) {
}
