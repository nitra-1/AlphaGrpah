export interface DiscoveryCandidate {
  symbol: string
  securityName: string | null
  dealCount: number
  distinctBuyers: number
  totalQuantity: number
  firstDealDate: string
  latestDealDate: string
}
