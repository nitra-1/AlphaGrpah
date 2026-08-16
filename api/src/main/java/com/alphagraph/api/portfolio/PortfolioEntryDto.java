package com.alphagraph.api.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A current holding plus its live mark-to-market value and P&L, and its current Rank/Score/Risk.
 * "Live" means as of the most recent NSE trading day's close (market.daily_prices) - there is no
 * intraday/real-time feed anywhere in this project - priceAsOfDate makes that explicit rather
 * than leaving "current" ambiguous. Price/P&L/score/risk fields are all null when not yet
 * available (no market data or no decision score computed yet for this instrument).
 *
 * <p>Position Health fields (from {@code positionHealth} onward, Module 3.3 follow-up) are all
 * null together whenever no Decision Score exists as of the holding's entry date
 * (see {@code PortfolioViewService.positionHealthFor}) - never guessed. {@code healthAnchorDate}/
 * {@code healthAnchorType} tell the caller exactly what the comparison is anchored to;
 * {@code healthAnchorType} is always {@code "FIRST_ENTRY"} in this slice, meaning "first time
 * AlphaGraph recorded this holding," not a broker-verified purchase date - see
 * {@code decision.api.PortfolioHolding.createdAt()}'s own javadoc.
 */
public record PortfolioEntryDto(
    UUID instrumentId, String symbol, BigDecimal quantity, BigDecimal avgBuyPrice,
    BigDecimal currentPrice, LocalDate priceAsOfDate, BigDecimal marketValue,
    BigDecimal unrealizedPnl, BigDecimal unrealizedPnlPercent,
    Double swingScore, String swingRating, Integer swingRank,
    Double longTermScore, String longTermRating, Integer longTermRank,
    String riskLevel, Double riskScore,
    String positionHealth, String healthReason, String attentionLevel,
    Double entrySwingScore, Double swingScoreChange,
    Integer entrySwingRank, Integer swingRankChange,
    String rankDeteriorationLevel, String rankDeteriorationBasis,
    LocalDate healthAnchorDate, String healthAnchorType,
    List<DomainDelta> domainDeltas
) {
}
