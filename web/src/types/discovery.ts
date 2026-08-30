export interface DiscoveryCandidate {
  symbol: string
  securityName: string | null
  dealCount: number
  distinctBuyers: number
  totalQuantity: number
  firstDealDate: string
  latestDealDate: string
  // Sprint 2: independently computed (see ownership.deals.DiscoveryReader) - the deal with the
  // highest blended materiality score isn't necessarily the deal with the highest raw ratio. Both
  // null when no deal for this symbol has been scored yet (e.g. fewer than 20 trading sessions of
  // price history so far), never a guessed placeholder.
  maxMaterialityScore: number | null
  maxMaterialityLevel: string | null
  largestDealToAdtvRatio: number | null
  // Sprint 3: from the latest ownership.institutional_interpretations row - all null until this
  // symbol's first interpretation runs. discoveryConfirmationState is 'NOT_APPLICABLE' (not null)
  // for a non-directional institutionalState (HIGH_CHURN/MIXED_ACTIVITY/NO_CLEAR_SIGNAL) - there's
  // nothing directional to confirm.
  eventStructure: string | null
  institutionalState: string | null
  discoveryConfirmationState: string | null
  interpretationConfidence: number | null
  churnState: string | null
}

// One persisted piece of evidence backing an interpretation.
export interface InterpretationReason {
  reasonCode: string
  metricValue: number | null
  evidenceReference: string | null
}

// The latest interpretation for one symbol, plus its reason codes - for the Discovery page's
// "Why?" expandable section. Every confirmation-specific field is null when
// discoveryConfirmationState is 'NOT_APPLICABLE'.
export interface InstitutionalInterpretationDetail {
  symbol: string
  asOfDate: string
  eventStructure: string
  institutionalState: string
  discoveryConfirmationState: string
  confirmationFrozen: boolean
  eventAnchorDate: string | null
  confirmationSessionsElapsed: number
  confirmationScore: number | null
  priceConfirmationScore: number | null
  deliveryConfirmationScore: number | null
  volumeConfirmationScore: number | null
  repeatActivityConfirmationScore: number | null
  confirmationCoveragePct: number | null
  confidence: number
  materialityScore: number | null
  reportedFlowState: string | null
  churnState: string
  institutionalBuyValue: number
  institutionalSellValue: number
  institutionalBuyerCount: number
  institutionalSellerCount: number
  reasons: InterpretationReason[]
}

// One individual deal for the Discovery expand-on-click section. Materiality fields are null when
// the deal hasn't been scored yet.
export interface DiscoveryDealDetail {
  id: string
  dealDate: string
  clientName: string
  buySell: 'BUY' | 'SELL'
  quantity: number
  price: number
  dealValue: number
  dealType: 'BULK' | 'BLOCK'
  materialityScore: number | null
  materialityLevel: string | null
  dealToAdtvRatio: number | null
  reportedFlowState: string | null
}
