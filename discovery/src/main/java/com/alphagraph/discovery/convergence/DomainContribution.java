package com.alphagraph.discovery.convergence;

import java.time.LocalDate;
import java.util.List;

/**
 * One domain's resolved contribution to a convergence snapshot - always present for all 5
 * {@link ConvergenceDomain} values, even when a domain has nothing (status {@code
 * NO_ACTIVE_SEQUENCE}/{@code INSUFFICIENT_DATA}), so a snapshot's full domain picture never
 * relies on inferring absence. {@code domainStrength}/{@code strongestPhase} come from the
 * single qualifying sequence with the highest {@code sequenceStrength} (the "representative"
 * sequence) plus a small breadth bonus for additional qualifying sequences in the same domain -
 * multiple sequences in one domain are never summed (user's Stage 4 spec §8, would otherwise let
 * one rich domain overwhelm the detector). {@code earliestSequenceDate}/{@code
 * latestSequenceDate} are {@code firstStepDate}/{@code lastStepDate}-based across qualifying
 * sequences, never {@code as_of_date}-based (round-3 freshness guard).
 */
record DomainContribution(
    ConvergenceDomain domain, DomainContributionStatus status, int activeSequenceCount,
    SequencePhase strongestPhase, Double domainStrength, Double domainConfidence,
    LocalDate earliestSequenceDate, LocalDate latestSequenceDate, Double contributionScore,
    List<SequenceContribution> sequences
) {
    boolean isActive() {
        return status == DomainContributionStatus.ACTIVE;
    }
}
