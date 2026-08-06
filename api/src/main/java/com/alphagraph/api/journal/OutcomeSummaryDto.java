package com.alphagraph.api.journal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregate outcomes across every closed (SELL) trade in the Trade Journal (Module 3.9) - an
 * open position has no outcome yet, so only SELL entries count here. All fields are null/empty
 * when no trade has ever been closed, rather than fabricating a 0% win rate out of zero trades.
 * averageWin/averageLoss are kept separate rather than one blended average - "average P&L" alone
 * hides whether a positive total came from many small wins or one large one.
 */
public record OutcomeSummaryDto(
    BigDecimal totalRealizedPnl, int closedTradeCount,
    int winCount, int lossCount, int breakEvenCount, Double winRatePercent,
    BigDecimal averageWin, BigDecimal averageLoss,
    TradeJournalEntryDto bestTrade, TradeJournalEntryDto worstTrade,
    List<InstrumentOutcomeDto> byInstrument
) {
}
