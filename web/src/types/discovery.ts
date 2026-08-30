export interface DiscoveryCandidate {
  symbol: string
  securityName: string | null
  dealCount: number
  distinctBuyers: number
  distinctSellers: number
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
  // LENSKART investigation fix: threaded into the main row (not just the detail endpoint) so the
  // confirmation badge can show "T+n of 5" here, where the badge is actually rendered. 0/false
  // when discoveryConfirmationState is 'NOT_APPLICABLE' (nothing directional running).
  confirmationSessionsElapsed: number
  confirmationFrozen: boolean
  // LENSKART investigation fix: 'PENDING_DATA' means at least one deal in this interpretation's
  // window hasn't been scored by Sprint 2 yet (e.g. its symbol has fewer than 20 real trading
  // sessions before the deal date) - so a landed state like NO_CLEAR_SIGNAL never silently reads
  // as a confident final answer when it's actually still waiting on upstream data. 'READY'
  // otherwise, including for symbols with no interpretation yet (institutionalState null).
  interpretationReadiness: 'READY' | 'PENDING_DATA' | null
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
  interpretationReadiness: 'READY' | 'PENDING_DATA'
  reasons: InterpretationReason[]
}

// One individual deal for the Discovery expand-on-click section. Materiality fields are null when
// the deal hasn't been scored yet. isDuplicate is true when this deal is a confirmed cross-feed
// BULK/BLOCK duplicate of another row for the same real trade - still shown here for audit, but
// excluded from every symbol-level aggregate (deal count, distinct buyers/sellers, etc.).
export interface DiscoveryDealDetail {
  id: string
  dealDate: string
  clientName: string
  buySell: 'BUY' | 'SELL'
  quantity: number
  price: number
  dealValue: number
  dealType: 'BULK' | 'BLOCK'
  isDuplicate: boolean
  materialityScore: number | null
  materialityLevel: string | null
  dealToAdtvRatio: number | null
  reportedFlowState: string | null
}
