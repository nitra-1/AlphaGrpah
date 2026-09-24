package com.alphagraph.api.opportunities;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The full Opportunity Detail page payload: Stage 5 lifecycle summary, Stage 4 convergence (with
 * its per-domain contributions), the Stage 1-3 causal chain per domain (anchored to the
 * convergence snapshot's own {@code asOfDate}, never "now" - see {@code decision.opportunity.
 * OpportunityDomainDetailAssembler}), and the Stage 5 transition timeline.
 */
public record OpportunityDetailDto(
    UUID instrumentId, String symbol,
    LocalDate lifecycleAsOfDate, String lifecycleState, String lifecycleReadiness, String trajectoryDirection,
    String peakLifecycleStage, Double lifecycleStrength, Double trajectoryScore,
    LocalDate lifecycleStartedDate, LocalDate stateStartedDate, Integer lifecycleAgeDays,
    List<ReasonNoteDto> lifecycleReasons,
    LocalDate convergenceAsOfDate, String convergenceState, String preContradictionState,
    Integer activeDomainCount, Integer domainCoverageCount, Integer domainCoveragePct, Double convergenceScore,
    List<DomainContributionDto> domainContributions, List<ReasonNoteDto> convergenceReasons,
    List<OpportunityDomainDetailDto> domainDetails,
    List<LifecycleTransitionDto> transitions
) {
}
