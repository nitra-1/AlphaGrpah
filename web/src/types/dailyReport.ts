export interface DailyReport {
  reportDate: string
  narrative: string
  topGainerSymbol: string | null
  topGainerRankImprovement: number | null
  topDeclinerSymbol: string | null
  topDeclinerRankDecline: number | null
  newEventCount: number
  guidanceChangeCount: number
  positiveNewsCount: number
  negativeNewsCount: number
  generatedAt: string
}
