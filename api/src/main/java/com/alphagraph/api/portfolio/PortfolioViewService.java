package com.alphagraph.api.portfolio;

import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.api.PortfolioHolding;
import com.alphagraph.decision.engine.DecisionScoreReader;
import com.alphagraph.decision.portfolio.PortfolioService;
import com.alphagraph.market.api.DailyPrice;
import com.alphagraph.market.api.DailyPriceReader;
import com.alphagraph.risk.api.RiskScore;
import com.alphagraph.risk.engine.RiskScoreReader;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Assembles {@link PortfolioEntryDto}s by calling decision.portfolio.PortfolioService (owns the
 * holdings), market.api.DailyPriceReader (live price), decision.engine.DecisionScoreReader
 * (Rank/Score) and risk.engine.RiskScoreReader (risk level) directly - the same "api module
 * assembles DTOs from domain modules' own readers" pattern api.dashboard.DashboardService and
 * api.watchlist.WatchlistViewService already established.
 */
@Service
public class PortfolioViewService {

    private final PortfolioService portfolioService;
    private final DailyPriceReader dailyPriceReader;
    private final DecisionScoreReader decisionScoreReader;
    private final RiskScoreReader riskScoreReader;

    public PortfolioViewService(
        PortfolioService portfolioService, DailyPriceReader dailyPriceReader,
        DecisionScoreReader decisionScoreReader, RiskScoreReader riskScoreReader
    ) {
        this.portfolioService = portfolioService;
        this.dailyPriceReader = dailyPriceReader;
        this.decisionScoreReader = decisionScoreReader;
        this.riskScoreReader = riskScoreReader;
    }

    public List<PortfolioEntryDto> list(UUID userId) {
        return portfolioService.list(userId).stream().map(this::toDto).toList();
    }

    public Optional<PortfolioEntryDto> buy(UUID userId, UUID instrumentId, BigDecimal quantity, BigDecimal price, String rationale) {
        return portfolioService.buy(userId, instrumentId, quantity, price, rationale).map(this::toDto);
    }

    public Optional<PortfolioEntryDto> sell(UUID userId, UUID instrumentId, BigDecimal quantity, BigDecimal price, String rationale) {
        return portfolioService.sell(userId, instrumentId, quantity, price, rationale).map(this::toDto);
    }

    private PortfolioEntryDto toDto(PortfolioHolding holding) {
        Optional<DailyPrice> latestPrice = dailyPriceReader.findLatest(holding.instrumentId());
        Optional<DecisionScore> score = decisionScoreReader.findLatest(holding.instrumentId());
        Optional<RiskScore> risk = riskScoreReader.findLatest(holding.instrumentId());

        // A fully-closed position (PortfolioService.sell's transient zero-quantity confirmation
        // value) has no market value or P&L left to report - nothing "unrealized" remains once
        // sold, and quantity 0 would otherwise divide-by-zero computing the P&L percentage.
        boolean isOpenPosition = holding.quantity().signum() > 0;
        BigDecimal currentPrice = latestPrice.map(DailyPrice::close).orElse(null);
        BigDecimal marketValue = !isOpenPosition || currentPrice == null ? null : holding.quantity().multiply(currentPrice);
        BigDecimal unrealizedPnl = marketValue == null ? null : marketValue.subtract(holding.quantity().multiply(holding.avgBuyPrice()));
        BigDecimal unrealizedPnlPercent = unrealizedPnl == null ? null
            : unrealizedPnl.divide(holding.quantity().multiply(holding.avgBuyPrice()), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        return new PortfolioEntryDto(
            holding.instrumentId(), holding.symbol(), holding.quantity(), holding.avgBuyPrice(),
            currentPrice, latestPrice.map(DailyPrice::tradeDate).orElse(null), marketValue,
            unrealizedPnl, unrealizedPnlPercent,
            score.map(DecisionScore::swingScore).orElse(null),
            score.map(s -> s.swingRating().name()).orElse(null),
            score.map(DecisionScore::swingRank).orElse(null),
            score.map(DecisionScore::longTermScore).orElse(null),
            score.map(s -> s.longTermRating().name()).orElse(null),
            score.map(DecisionScore::longTermRank).orElse(null),
            risk.map(r -> r.overallRisk().name()).orElse(null),
            risk.map(RiskScore::riskScore).orElse(null)
        );
    }
}
