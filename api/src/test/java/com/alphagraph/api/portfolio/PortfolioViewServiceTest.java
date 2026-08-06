package com.alphagraph.api.portfolio;

import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.api.PortfolioHolding;
import com.alphagraph.decision.engine.DecisionScoreReader;
import com.alphagraph.decision.portfolio.PortfolioService;
import com.alphagraph.market.api.DailyPrice;
import com.alphagraph.market.api.DailyPriceReader;
import com.alphagraph.risk.api.RiskLevel;
import com.alphagraph.risk.api.RiskScore;
import com.alphagraph.risk.engine.RiskScoreReader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioViewServiceTest {

    private final PortfolioService portfolioService = mock(PortfolioService.class);
    private final DailyPriceReader dailyPriceReader = mock(DailyPriceReader.class);
    private final DecisionScoreReader decisionScoreReader = mock(DecisionScoreReader.class);
    private final RiskScoreReader riskScoreReader = mock(RiskScoreReader.class);
    private final PortfolioViewService viewService =
        new PortfolioViewService(portfolioService, dailyPriceReader, decisionScoreReader, riskScoreReader);

    private final UUID instrumentId = UUID.randomUUID();

    @Test
    void computesMarketValueAndUnrealizedPnlAgainstTheLatestClose() {
        PortfolioHolding holding = new PortfolioHolding(UUID.randomUUID(), instrumentId, "TCS", new BigDecimal("10"), new BigDecimal("100"), Instant.now());
        when(portfolioService.list()).thenReturn(List.of(holding));
        when(dailyPriceReader.findLatest(instrumentId)).thenReturn(Optional.of(priceOf(new BigDecimal("120"))));
        when(decisionScoreReader.findLatest(instrumentId)).thenReturn(Optional.empty());
        when(riskScoreReader.findLatest(instrumentId)).thenReturn(Optional.empty());

        PortfolioEntryDto dto = viewService.list().get(0);

        assertThat(dto.currentPrice()).isEqualByComparingTo(new BigDecimal("120"));
        assertThat(dto.marketValue()).isEqualByComparingTo(new BigDecimal("1200")); // 10 * 120
        assertThat(dto.unrealizedPnl()).isEqualByComparingTo(new BigDecimal("200")); // 1200 - 1000
        assertThat(dto.unrealizedPnlPercent()).isEqualByComparingTo(new BigDecimal("20")); // 200/1000 * 100
    }

    @Test
    void unrealizedPnlIsNegativeWhenThePriceHasFallenBelowCostBasis() {
        PortfolioHolding holding = new PortfolioHolding(UUID.randomUUID(), instrumentId, "TCS", new BigDecimal("10"), new BigDecimal("100"), Instant.now());
        when(portfolioService.list()).thenReturn(List.of(holding));
        when(dailyPriceReader.findLatest(instrumentId)).thenReturn(Optional.of(priceOf(new BigDecimal("80"))));
        when(decisionScoreReader.findLatest(instrumentId)).thenReturn(Optional.empty());
        when(riskScoreReader.findLatest(instrumentId)).thenReturn(Optional.empty());

        PortfolioEntryDto dto = viewService.list().get(0);

        assertThat(dto.unrealizedPnl()).isEqualByComparingTo(new BigDecimal("-200"));
        assertThat(dto.unrealizedPnlPercent()).isEqualByComparingTo(new BigDecimal("-20"));
    }

    @Test
    void priceAndPnlFieldsAreNullWithoutAnyMarketDataYet() {
        PortfolioHolding holding = new PortfolioHolding(UUID.randomUUID(), instrumentId, "NEWCO", new BigDecimal("5"), new BigDecimal("50"), Instant.now());
        when(portfolioService.list()).thenReturn(List.of(holding));
        when(dailyPriceReader.findLatest(instrumentId)).thenReturn(Optional.empty());
        when(decisionScoreReader.findLatest(instrumentId)).thenReturn(Optional.empty());
        when(riskScoreReader.findLatest(instrumentId)).thenReturn(Optional.empty());

        PortfolioEntryDto dto = viewService.list().get(0);

        assertThat(dto.currentPrice()).isNull();
        assertThat(dto.marketValue()).isNull();
        assertThat(dto.unrealizedPnl()).isNull();
        assertThat(dto.unrealizedPnlPercent()).isNull();
    }

    @Test
    void aFullyClosedPositionHasNoMarketValueOrPnlRatherThanDividingByZero() {
        // What PortfolioService.sell returns for a full sell - quantity is the transient zero confirmation value.
        PortfolioHolding closedHolding = new PortfolioHolding(UUID.randomUUID(), instrumentId, "TCS", BigDecimal.ZERO, new BigDecimal("100"), Instant.now());
        when(portfolioService.sell(instrumentId, new BigDecimal("10"), new BigDecimal("120"), null)).thenReturn(Optional.of(closedHolding));
        when(dailyPriceReader.findLatest(instrumentId)).thenReturn(Optional.of(priceOf(new BigDecimal("120"))));
        when(decisionScoreReader.findLatest(instrumentId)).thenReturn(Optional.empty());
        when(riskScoreReader.findLatest(instrumentId)).thenReturn(Optional.empty());

        PortfolioEntryDto dto = viewService.sell(instrumentId, new BigDecimal("10"), new BigDecimal("120"), null).orElseThrow();

        assertThat(dto.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.marketValue()).isNull();
        assertThat(dto.unrealizedPnl()).isNull();
        assertThat(dto.unrealizedPnlPercent()).isNull();
    }

    @Test
    void enrichesWithCurrentScoreRankAndRiskLevelWhenAvailable() {
        PortfolioHolding holding = new PortfolioHolding(UUID.randomUUID(), instrumentId, "TCS", new BigDecimal("10"), new BigDecimal("100"), Instant.now());
        when(portfolioService.list()).thenReturn(List.of(holding));
        when(dailyPriceReader.findLatest(instrumentId)).thenReturn(Optional.empty());
        when(decisionScoreReader.findLatest(instrumentId)).thenReturn(Optional.of(scoreOf()));
        when(riskScoreReader.findLatest(instrumentId)).thenReturn(Optional.of(riskOf()));

        PortfolioEntryDto dto = viewService.list().get(0);

        assertThat(dto.swingScore()).isEqualTo(85.16);
        assertThat(dto.swingRating()).isEqualTo("STRONG_BUY");
        assertThat(dto.swingRank()).isEqualTo(1);
        assertThat(dto.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void buyAndSellDelegateToTheDomainServiceAndEnrichTheResult() {
        PortfolioHolding holding = new PortfolioHolding(UUID.randomUUID(), instrumentId, "TCS", new BigDecimal("10"), new BigDecimal("100"), Instant.now());
        when(portfolioService.buy(instrumentId, new BigDecimal("10"), new BigDecimal("100"), "rationale")).thenReturn(Optional.of(holding));
        when(portfolioService.sell(instrumentId, new BigDecimal("5"), new BigDecimal("110"), null)).thenReturn(Optional.of(holding));
        when(dailyPriceReader.findLatest(instrumentId)).thenReturn(Optional.empty());
        when(decisionScoreReader.findLatest(instrumentId)).thenReturn(Optional.empty());
        when(riskScoreReader.findLatest(instrumentId)).thenReturn(Optional.empty());

        assertThat(viewService.buy(instrumentId, new BigDecimal("10"), new BigDecimal("100"), "rationale")).isPresent();
        assertThat(viewService.sell(instrumentId, new BigDecimal("5"), new BigDecimal("110"), null)).isPresent();
    }

    private DailyPrice priceOf(BigDecimal close) {
        return new DailyPrice(instrumentId, "TCS", LocalDate.of(2026, 6, 1), close, close, close, close, 1000L, null);
    }

    private DecisionScore scoreOf() {
        return new DecisionScore(
            instrumentId, "TCS", LocalDate.of(2026, 6, 1),
            85.16, DecisionRating.STRONG_BUY, 1,
            72.35, DecisionRating.BUY, 1,
            100.0, 85.0, 60.0, 100.0, 48.75, null,
            85.0, 1, Instant.now()
        );
    }

    private RiskScore riskOf() {
        return new RiskScore(
            instrumentId, "TCS", LocalDate.of(2026, 6, 1),
            RiskLevel.LOW, RiskLevel.LOW, RiskLevel.LOW, RiskLevel.LOW, RiskLevel.LOW,
            75.0, 90.0, null, null, null, 1, Instant.now()
        );
    }
}
