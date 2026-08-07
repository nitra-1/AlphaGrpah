export interface WatchlistEntry {
  instrumentId: string
  symbol: string
  addedAt: string
  swingScore: number | null
  swingRating: string | null
  swingRank: number | null
  longTermScore: number | null
  longTermRating: string | null
  longTermRank: number | null
}
