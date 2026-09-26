/** Mirrors api.admin.NewsDiscoverySummaryDto exactly. */
export interface NewsDiscoverySummary {
  totalProcessed: number
  totalProcessedPreviousDay: number
  economyRelevantCount: number
  economyRelevantCountPreviousDay: number
  uniqueEconomicEventCount: number
  uniqueEconomicEventCountPreviousDay: number
  sectorsImpactedCount: number
  sectorsImpactedCountPreviousDay: number
  companiesIdentifiedTracked: number
  companiesIdentifiedUntracked: number
  companiesIdentifiedPreviousDay: number
}

/** Mirrors api.admin.EconomicEventSummaryDto exactly - one row of the Latest Economic Events feed. */
export interface EconomicEventSummary {
  id: string
  theme: string
  economicRelevance: string
  direction: string
  magnitude: string
  confidence: number
  horizon: string
  sourceArticleCount: number
  publishedAt: string
  computedAt: string
}

/** Mirrors api.admin.SectorImpactDetailDto exactly. */
export interface SectorImpactDetail {
  sectorId: string | null
  sectorName: string
  direction: string
  strength: string
  confidence: number
  mechanism: string | null
}

/** Mirrors api.admin.CompanyExposureDetailDto exactly. */
export interface CompanyExposureDetail {
  matchedInstrumentId: string | null
  matchedSecurityMasterId: string | null
  matchedSymbol: string | null
  tracked: boolean
  companyNameRaw: string
  matchType: string
  exposureType: string
  direction: string
  impactStrength: string
  confidence: number
  reason: string | null
}

/** Mirrors api.admin.NewsSourceArticleDto exactly. */
export interface NewsSourceArticle {
  documentId: string
  title: string | null
  sourceUrl: string
  announcedAt: string
}

/** Mirrors api.admin.EconomicEventDetailDto exactly. */
export interface EconomicEventDetail {
  id: string
  theme: string
  economicRelevance: string
  direction: string
  magnitude: string
  confidence: number
  horizon: string
  computedAt: string
  sectorImpacts: SectorImpactDetail[]
  companyExposures: CompanyExposureDetail[]
  sourceArticles: NewsSourceArticle[]
}

/** Mirrors api.admin.SectorImpactMapEntryDto exactly. */
export interface SectorImpactMapEntry {
  sectorId: string | null
  sectorName: string
  direction: string
  strength: string
  contributingEventCount: number
}

/** Mirrors api.admin.NewsDiscoveryCandidateDto exactly. */
export interface NewsDiscoveryCandidate {
  symbol: string
  companyName: string
  securityMasterId: string
  tracked: boolean
  firstSeenAt: string
  lastSeenAt: string
  exposureCount: number
  bestDirection: string
  bestExposureType: string
  status: string
}
