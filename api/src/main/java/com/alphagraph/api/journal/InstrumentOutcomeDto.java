package com.alphagraph.api.journal;

import java.math.BigDecimal;
import java.util.UUID;

/** One instrument's realized-trade record - closedTradeCount/winCount/lossCount count SELL entries only (a BUY has no outcome yet). */
public record InstrumentOutcomeDto(
    UUID instrumentId, String symbol, BigDecimal realizedPnl,
    int closedTradeCount, int winCount, int lossCount, Double winRatePercent
) {
}
