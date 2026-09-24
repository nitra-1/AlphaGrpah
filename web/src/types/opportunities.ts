/** Mirrors api.opportunities.OpportunityEntryDto exactly. */
export interface OpportunityEntry {
  instrumentId: string
  symbol: string
  asOfDate: string | null
  lifecycleState: string | null
  lifecycleReadiness: string | null
  trajectoryDirection: string | null
  lifecycleStrength: number | null
  trajectoryScore: number | null
  currentConvergenceScore: number | null
  currentActiveDomains: number | null
  lifecycleAgeDays: number | null
}

/** Mirrors api.opportunities.ReasonNoteDto exactly. */
export interface ReasonNote {
  reasonCode: string
  metricName: string | null
  metricValue: number | null
  evidenceDate: string | null
  evidenceReference: string | null
}

/** Mirrors api.opportunities.EvidenceObservationDto exactly - one Stage 1 evidence row. */
export interface EvidenceObservation {
  metricName: string
  asOfDate: string
  value: number | null
  priorValue: number | null
  change: number | null
  confidence: number | null
  source: string | null
}

/** Mirrors api.opportunities.InflectionStateDto exactly - one Stage 2 inflection row. */
export interface InflectionState {
  primaryState: string
  drivingMetric: string | null
  level: number | null
  change: number | null
  velocityBand: string | null
  persistence: number | null
  confidence: number | null
  asOfDate: string
  reasons: ReasonNote[]
}

/** Mirrors api.opportunities.TransformationSequenceDto exactly - one Stage 3 sequence row. */
export interface TransformationSequence {
  sequenceType: string
  sequencePhase: string
  currentStep: number
  totalSteps: number
  firstStepDate: string | null
  lastStepDate: string | null
  sequenceStrength: number | null
  confidence: number | null
  reasons: ReasonNote[]
}

/** Mirrors api.opportunities.DomainContributionDto exactly - one domain's Stage 4 contribution. */
export interface DomainContribution {
  domain: string
  contributionStatus: string
  activeSequenceCount: number
  strongestPhase: string | null
  domainStrength: number | null
  domainConfidence: number | null
  contributionScore: number | null
  evidenceReference: string | null
}

/** Mirrors api.opportunities.OpportunityDomainDetailDto exactly - one domain's full Stage 1-4 causal chain. */
export interface OpportunityDomainDetail {
  domain: string
  evidence: EvidenceObservation[]
  inflection: InflectionState | null
  sequences: TransformationSequence[]
  convergenceContribution: DomainContribution | null
}

/** Mirrors api.opportunities.LifecycleTransitionDto exactly - one real Stage 5 state change. */
export interface LifecycleTransition {
  transitionDate: string
  fromState: string | null
  toState: string
  triggerReason: string
  convergenceScore: number | null
  activeDomainCount: number | null
}

/** Mirrors api.opportunities.OpportunityDetailDto exactly. */
export interface OpportunityDetail {
  instrumentId: string
  symbol: string
  lifecycleAsOfDate: string
  lifecycleState: string | null
  lifecycleReadiness: string | null
  trajectoryDirection: string | null
  peakLifecycleStage: string | null
  lifecycleStrength: number | null
  trajectoryScore: number | null
  lifecycleStartedDate: string | null
  stateStartedDate: string | null
  lifecycleAgeDays: number | null
  lifecycleReasons: ReasonNote[]
  convergenceAsOfDate: string | null
  convergenceState: string | null
  preContradictionState: string | null
  activeDomainCount: number | null
  domainCoverageCount: number | null
  domainCoveragePct: number | null
  convergenceScore: number | null
  domainContributions: DomainContribution[]
  convergenceReasons: ReasonNote[]
  domainDetails: OpportunityDomainDetail[]
  transitions: LifecycleTransition[]
}
