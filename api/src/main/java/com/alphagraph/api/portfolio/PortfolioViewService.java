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
import java.time.LocalDate;
import java.time.ZoneId;
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

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final PortfolioService portfolioService;
    private final DailyPriceReader dailyPriceReader;
    private final DecisionScoreReader decisionScoreReader;
    private final RiskScoreReader riskScoreReader;
    private final PositionHealthClassifier positionHealthClassifier;

    public PortfolioViewService(
        PortfolioService portfolioService, DailyPriceReader dailyPriceReader,
        DecisionScoreReader decisionScoreReader, RiskScoreReader riskScoreReader,
        PositionHealthClassifier positionHealthClassifier
    ) {
        this.portfolioService = portfolioService;
        this.dailyPriceReader = dailyPriceReader;
        this.decisionScoreReader = decisionScoreReader;
        this.riskScoreReader = riskScoreReader;
        this.positionHealthClassifier = positionHealthClassifier;
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

    public boolean remove(UUID userId, UUID instrumentId) {
        return portfolioService.remove(userId, instrumentId);
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

        PositionHealthResult health = score.flatMap(currentScore -> positionHealthFor(holding, currentScore)).orElse(null);

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
            risk.map(RiskScore::riskScore).orElse(null),
            health == null ? null : health.positionHealth().name(),
            health == null ? null : mapOrNull(health.healthReason()),
            health == null ? null : health.attentionLevel().name(),
            health == null ? null : health.entrySwingScore(),
            health == null ? null : health.swingScoreChange(),
            health == null ? null : health.entrySwingRank(),
            health == null ? null : health.swingRankChange(),
            health == null ? null : mapOrNull(health.rankDeteriorationLevel()),
            health == null ? null : mapOrNull(health.rankDeteriorationBasis()),
            health == null ? null : health.healthAnchorDate(),
            health == null ? null : health.healthAnchorType(),
            health == null ? List.of() : health.domainDeltas()
        );
    }

    /** Position Health is anchored to the holding's first-recorded entry (see {@code PortfolioHolding.createdAt}'s javadoc for exactly what that does and doesn't prove) - null when no score exists as of that date (instrument added to tracking after entry, or a real coverage gap), never guessed. */
    private Optional<PositionHealthResult> positionHealthFor(PortfolioHolding holding, DecisionScore currentScore) {
        LocalDate entryDate = holding.createdAt().atZone(IST).toLocalDate();
        return decisionScoreReader.findAsOf(holding.instrumentId(), entryDate)
            .map(entryScore -> positionHealthClassifier.classify(entryScore, currentScore));
    }

    private static String mapOrNull(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
