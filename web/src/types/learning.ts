export interface RatingHitRate {
  rating: string
  horizonDays: number
  sampleSize: number
  hitRatePercentage: number | null
  sufficientData: boolean
}

export interface LearningEvidenceSummary {
  daysOfHistory: number
  decisionSnapshotCount: number
  forwardOutcomesComputed: number
  swingRatingHitRates: RatingHitRate[]
  longTermRatingHitRates: RatingHitRate[]
}
