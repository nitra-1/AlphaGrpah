package com.alphagraph.api.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A current holding plus its live mark-to-market value and P&L, and its current Rank/Score/Risk.
 * "Live" means as of the most recent NSE trading day's close (market.daily_prices) - there is no
 * intraday/real-time feed anywhere in this project - priceAsOfDate makes that explicit rather
 * than leaving "current" ambiguous. Price/P&L/score/risk fields are all null when not yet
 * available (no market data or no decision score computed yet for this instrument).
 */
public record PortfolioEntryDto(
    UUID instrumentId, String symbol, BigDecimal quantity, BigDecimal avgBuyPrice,
    BigDecimal currentPrice, LocalDate priceAsOfDate, BigDecimal marketValue,
    BigDecimal unrealizedPnl, BigDecimal unrealizedPnlPercent,
    Double swingScore, String swingRating, Integer swingRank,
    Double longTermScore, String longTermRating, Integer longTermRank,
    String riskLevel, Double riskScore
) {
}
