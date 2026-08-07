export interface TradeJournalEntry {
  instrumentId: string
  symbol: string
  action: string
  quantity: number
  price: number
  tradeValue: number
  costBasisPrice: number | null
  realizedPnl: number | null
  rationale: string | null
  createdAt: string
}

export interface InstrumentOutcome {
  instrumentId: string
  symbol: string
  realizedPnl: number
  closedTradeCount: number
  winCount: number
  lossCount: number
  winRatePercent: number | null
}

export interface OutcomeSummary {
  totalRealizedPnl: number | null
  closedTradeCount: number
  winCount: number
  lossCount: number
  breakEvenCount: number
  winRatePercent: number | null
  averageWin: number | null
  averageLoss: number | null
  bestTrade: TradeJournalEntry | null
  worstTrade: TradeJournalEntry | null
  byInstrument: InstrumentOutcome[]
}
