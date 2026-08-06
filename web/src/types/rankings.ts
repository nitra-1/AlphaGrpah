/** Mirrors api.rankings.RankingEntryDto exactly. */
export interface RankingEntry {
  instrumentId: string
  symbol: string
  asOfDate: string | null
  technicalScore: number | null
  fundamentalScore: number | null
  institutionalScore: number | null
  sectorScore: number | null
  riskScore: number | null
  corporateScore: number | null
  swingScore: number | null
  swingRating: string | null
  swingRank: number | null
  longTermScore: number | null
  longTermRating: string | null
  longTermRank: number | null
  confidence: number | null
}
