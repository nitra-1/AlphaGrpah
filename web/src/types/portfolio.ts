export interface DomainDelta {
  domain: string
  entryValue: number
  currentValue: number
  delta: number
}

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
  // Position Health (v1) - null together when no Decision Score exists as of this holding's
  // entry date. healthAnchorType is always "FIRST_ENTRY" here, meaning "first time AlphaGraph
  // recorded this holding," not necessarily the investor's actual purchase date.
  positionHealth: string | null
  healthReason: string | null
  attentionLevel: string | null
  entrySwingScore: number | null
  swingScoreChange: number | null
  entrySwingRank: number | null
  swingRankChange: number | null
  rankDeteriorationLevel: string | null
  rankDeteriorationBasis: string | null
  healthAnchorDate: string | null
  healthAnchorType: string | null
  domainDeltas: DomainDelta[]
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
