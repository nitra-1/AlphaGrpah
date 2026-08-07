export interface PortfolioEntry {
  instrumentId: string
  symbol: string
  quantity: number
  avgBuyPrice: number
  currentPrice: number | null
  priceAsOfDate: string | null
  marketValue: number | null
  unrealizedPnl: number | null
  unrealizedPnlPercent: number | null
  swingScore: number | null
  swingRating: string | null
  swingRank: number | null
  longTermScore: number | null
  longTermRating: string | null
  longTermRank: number | null
  riskLevel: string | null
  riskScore: number | null
}

export interface SectorExposure {
  sectorName: string
  marketValue: number
  percentOfPortfolio: number
}

export interface PortfolioRisk {
  totalMarketValue: number | null
  weightedRiskScore: number | null
  weightedRiskLevel: string | null
  riskScoreCoveragePercent: number | null
  topHoldingSymbol: string | null
  topHoldingConcentrationPercent: number | null
  holdingConcentrationLevel: string | null
  topSectorName: string | null
  topSectorConcentrationPercent: number | null
  sectorConcentrationLevel: string | null
  sectorBreakdown: SectorExposure[]
}
