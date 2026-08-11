export interface TrackedInstrument {
  id: string
  symbol: string
  name: string
  sectorId: string | null
  sectorName: string | null
}

export interface AddFinancialResultRequest {
  symbol: string
  periodEnd: string
  periodType: 'QUARTERLY' | 'ANNUAL'
  sales: number
  pat: number
  eps?: number
  roePercentage?: number
  rocePercentage?: number
  operatingMarginPercentage?: number
  netMarginPercentage?: number
  cashFlowFromOperations?: number
  totalAssets?: number
  currentAssets?: number
  currentLiabilities?: number
  totalDebt?: number
  totalEquity?: number
  interestExpense?: number
  ebit?: number
}

export interface AddShareholdingRequest {
  symbol: string
  periodEnd: string
  promoterPercentage: number
  fiiPercentage: number
  diiPercentage: number
  mfPercentage?: number
  publicPercentage?: number
}
