package com.alphagraph.api.opportunities;

/** Mirrors {@code decision.api.DomainContribution} exactly - one domain's Stage 4 contribution row. */
public record DomainContributionDto(
    String domain, String contributionStatus, int activeSequenceCount, String strongestPhase,
    Double domainStrength, Double domainConfidence, Double contributionScore, String evidenceReference
) {
}
