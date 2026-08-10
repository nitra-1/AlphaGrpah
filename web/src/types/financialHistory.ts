export interface FinancialHistoryEntry {
  periodEnd: string
  periodType: string
  sales: number
  pat: number
  eps: number | null
  roePercentage: number | null
  rocePercentage: number | null
  operatingMarginPercentage: number | null
  netMarginPercentage: number | null
  cashFlowFromOperations: number | null
  totalAssets: number | null
  currentAssets: number | null
  currentLiabilities: number | null
  totalDebt: number | null
  totalEquity: number | null
  interestExpense: number | null
  ebit: number | null
}
