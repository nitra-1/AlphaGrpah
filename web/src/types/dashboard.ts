export interface BiggestOrder {
  symbol: string
  orderValueCrore: number | null
  customerName: string | null
  lifecycleStage: string | null
  detectedAt: string
}

export interface CorporateEvent {
  symbol: string
  eventType: string
  category: string | null
  summary: string
  revenueImpact: string | null
  signal: string
  extractedAt: string
}

export interface GuidanceChange {
  symbol: string
  metricType: string
  guidanceValue: string | null
  guidancePeriod: string | null
  direction: string
  commitmentLevel: string | null
  observedAt: string
}

export interface NewsItem {
  symbol: string
  direction: string
  signal: string
  impactSummary: string
  announcedAt: string
}

export interface TopCatalyst {
  symbol: string
  catalystScore: number
  catalystTrend: string
  recentCatalystCount: number
}

export interface GrowthVisibility {
  symbol: string
  growthVisibilityScore: number
  guidanceTrend: string
  managementCredibility: string
}

export interface CorporateScore {
  symbol: string
  corporateScore: number
  corporateRating: string
  orderBookScore: number | null
  managementScore: number | null
  newsCatalystScore: number | null
  eventNetSignal: number
}

export interface DashboardSummary {
  biggestOrders: BiggestOrder[]
  corporateEvents: CorporateEvent[]
  guidanceChanges: GuidanceChange[]
  positiveNews: NewsItem[]
  negativeNews: NewsItem[]
  topCatalysts: TopCatalyst[]
  growthVisibility: GrowthVisibility[]
  corporateScores: CorporateScore[]
}
