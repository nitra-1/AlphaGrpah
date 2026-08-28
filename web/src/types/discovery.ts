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
