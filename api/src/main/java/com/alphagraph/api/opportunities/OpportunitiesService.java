package com.alphagraph.api.opportunities;

import com.alphagraph.api.error.NotFoundException;
import com.alphagraph.decision.api.ConvergenceSnapshot;
import com.alphagraph.decision.api.DomainContribution;
import com.alphagraph.decision.api.EvidenceObservation;
import com.alphagraph.decision.api.InflectionState;
import com.alphagraph.decision.api.LifecycleSnapshot;
import com.alphagraph.decision.api.LifecycleTransition;
import com.alphagraph.decision.api.OpportunityDomainDetail;
import com.alphagraph.decision.api.ReasonNote;
import com.alphagraph.decision.api.TransformationSequence;
import com.alphagraph.decision.opportunity.OpportunityReader;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OpportunitiesService {

    private final OpportunityReader opportunityReader;

    public OpportunitiesService(OpportunityReader opportunityReader) {
        this.opportunityReader = opportunityReader;
    }

    /** Every tracked instrument's latest lifecycle classification. */
    public List<OpportunityEntryDto> list() {
        return opportunityReader.findAllLatestLifecycle().stream().map(OpportunitiesService::toEntryDto).toList();
    }

    /**
     * The full detail payload for one instrument. The Stage 1-3 causal chain is anchored to the
     * *convergence* snapshot's own {@code asOfDate} - the direct upstream of the domain
     * contributions being explained - never to "now" and never to the lifecycle snapshot's date
     * if the two ever diverge.
     */
    public OpportunityDetailDto detail(UUID instrumentId) {
        LifecycleSnapshot lifecycle = opportunityReader.findLatestLifecycle(instrumentId)
            .orElseThrow(() -> new NotFoundException("No lifecycle classification yet for instrument " + instrumentId));
        List<ReasonNote> lifecycleReasons = opportunityReader.findLifecycleReasons(instrumentId);
        List<LifecycleTransition> transitions = opportunityReader.findTransitions(instrumentId);

        Optional<ConvergenceSnapshot> convergence = opportunityReader.findLatestConvergence(instrumentId);
        List<ReasonNote> convergenceReasons = convergence.isPresent() ? opportunityReader.findConvergenceReasons(instrumentId) : List.of();

        List<DomainContribution> contributions = convergence.map(ConvergenceSnapshot::domainContributions).orElse(List.of());
        LocalDate anchorDate = convergence.map(ConvergenceSnapshot::asOfDate).orElse(lifecycle.asOfDate());
        List<OpportunityDomainDetail> domainDetails = opportunityReader.findDomainDetails(instrumentId, anchorDate, contributions);

        return new OpportunityDetailDto(
            lifecycle.instrumentId(), lifecycle.symbol(),
            lifecycle.asOfDate(), lifecycle.lifecycleState(), lifecycle.lifecycleReadiness(), lifecycle.trajectoryDirection(),
            lifecycle.peakLifecycleStage(), lifecycle.lifecycleStrength(), lifecycle.trajectoryScore(),
            lifecycle.lifecycleStartedDate(), lifecycle.stateStartedDate(), lifecycle.lifecycleAgeDays(),
            lifecycleReasons.stream().map(OpportunitiesService::toReasonDto).toList(),
            convergence.map(ConvergenceSnapshot::asOfDate).orElse(null),
            convergence.map(ConvergenceSnapshot::convergenceState).orElse(null),
            convergence.map(ConvergenceSnapshot::preContradictionState).orElse(null),
            convergence.map(ConvergenceSnapshot::activeDomainCount).orElse(null),
            convergence.map(ConvergenceSnapshot::domainCoverageCount).orElse(null),
            convergence.map(ConvergenceSnapshot::domainCoveragePct).orElse(null),
            convergence.map(ConvergenceSnapshot::convergenceScore).orElse(null),
            contributions.stream().map(OpportunitiesService::toContributionDto).toList(),
            convergenceReasons.stream().map(OpportunitiesService::toReasonDto).toList(),
            domainDetails.stream().map(OpportunitiesService::toDomainDetailDto).toList(),
            transitions.stream().map(OpportunitiesService::toTransitionDto).toList()
        );
    }

    private static OpportunityEntryDto toEntryDto(LifecycleSnapshot s) {
        return new OpportunityEntryDto(
            s.instrumentId(), s.symbol(), s.asOfDate(),
            s.lifecycleState(), s.lifecycleReadiness(), s.trajectoryDirection(),
            s.lifecycleStrength(), s.trajectoryScore(), s.currentConvergenceScore(),
            s.currentActiveDomains(), s.lifecycleAgeDays()
        );
    }

    private static ReasonNoteDto toReasonDto(ReasonNote r) {
        return new ReasonNoteDto(r.reasonCode(), r.metricName(), r.metricValue(), r.evidenceDate(), r.evidenceReference());
    }

    private static DomainContributionDto toContributionDto(DomainContribution c) {
        return new DomainContributionDto(
            c.domain(), c.contributionStatus(), c.activeSequenceCount(), c.strongestPhase(),
            c.domainStrength(), c.domainConfidence(), c.contributionScore(), c.evidenceReference()
        );
    }

    private static LifecycleTransitionDto toTransitionDto(LifecycleTransition t) {
        return new LifecycleTransitionDto(t.transitionDate(), t.fromState(), t.toState(), t.triggerReason(), t.convergenceScore(), t.activeDomainCount());
    }

    private static EvidenceObservationDto toEvidenceDto(EvidenceObservation e) {
        return new EvidenceObservationDto(e.metricName(), e.asOfDate(), e.value(), e.priorValue(), e.change(), e.confidence(), e.source());
    }

    private static InflectionStateDto toInflectionDto(InflectionState i) {
        return new InflectionStateDto(
            i.primaryState(), i.drivingMetric(), i.level(), i.change(), i.velocityBand(),
            i.persistence(), i.confidence(), i.asOfDate(), i.reasons().stream().map(OpportunitiesService::toReasonDto).toList()
        );
    }

    private static TransformationSequenceDto toSequenceDto(TransformationSequence s) {
        return new TransformationSequenceDto(
            s.sequenceType(), s.sequencePhase(), s.currentStep(), s.totalSteps(),
            s.firstStepDate(), s.lastStepDate(), s.sequenceStrength(), s.confidence(),
            s.reasons().stream().map(OpportunitiesService::toReasonDto).toList()
        );
    }

    private static OpportunityDomainDetailDto toDomainDetailDto(OpportunityDomainDetail d) {
        return new OpportunityDomainDetailDto(
            d.domain(),
            d.evidence().stream().map(OpportunitiesService::toEvidenceDto).toList(),
            d.inflection() == null ? null : toInflectionDto(d.inflection()),
            d.sequences().stream().map(OpportunitiesService::toSequenceDto).toList(),
            d.convergenceContribution() == null ? null : toContributionDto(d.convergenceContribution())
        );
    }
}
