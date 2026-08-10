package com.alphagraph.api.journal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutcomeTrackingServiceTest {

    private final TradeJournalViewService viewService = mock(TradeJournalViewService.class);
    private final OutcomeTrackingService service = new OutcomeTrackingService(viewService);

    private final UUID tcs = UUID.randomUUID();
    private final UUID infy = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void emptyJournalProducesAnAllNullEmptySummary() {
        when(viewService.list(userId)).thenReturn(List.of());

        OutcomeSummaryDto summary = service.compute(userId);

        assertThat(summary.totalRealizedPnl()).isNull();
        assertThat(summary.closedTradeCount()).isZero();
        assertThat(summary.winRatePercent()).isNull();
        assertThat(summary.bestTrade()).isNull();
        assertThat(summary.byInstrument()).isEmpty();
    }

    @Test
    void openPositionsWithOnlyBuysProduceNoOutcomesYet() {
        when(viewService.list(userId)).thenReturn(List.of(buy(tcs, "TCS")));

        OutcomeSummaryDto summary = service.compute(userId);

        assertThat(summary.closedTradeCount()).isZero();
        assertThat(summary.totalRealizedPnl()).isNull();
    }

    @Test
    void computesWinRateTotalPnlAndSeparateAverageWinAndLoss() {
        when(viewService.list(userId)).thenReturn(List.of(
            sell(tcs, "TCS", new BigDecimal("100")),  // win
            sell(tcs, "TCS", new BigDecimal("200")),  // win
            sell(infy, "INFY", new BigDecimal("-50")) // loss
        ));

        OutcomeSummaryDto summary = service.compute(userId);

        assertThat(summary.closedTradeCount()).isEqualTo(3);
        assertThat(summary.winCount()).isEqualTo(2);
        assertThat(summary.lossCount()).isEqualTo(1);
        assertThat(summary.breakEvenCount()).isZero();
        assertThat(summary.totalRealizedPnl()).isEqualByComparingTo(new BigDecimal("250")); // 100+200-50
        assertThat(summary.winRatePercent()).isEqualTo(66.67); // 2/3 * 100, rounded
        assertThat(summary.averageWin()).isEqualByComparingTo(new BigDecimal("150")); // (100+200)/2
        assertThat(summary.averageLoss()).isEqualByComparingTo(new BigDecimal("-50"));
    }

    @Test
    void breakEvenTradesAreCountedSeparatelyButStillCountTowardTheWinRateDenominator() {
        when(viewService.list(userId)).thenReturn(List.of(
            sell(tcs, "TCS", new BigDecimal("100")), // win
            sell(tcs, "TCS", BigDecimal.ZERO)        // break-even
        ));

        OutcomeSummaryDto summary = service.compute(userId);

        assertThat(summary.winCount()).isEqualTo(1);
        assertThat(summary.breakEvenCount()).isEqualTo(1);
        assertThat(summary.winRatePercent()).isEqualTo(50.0); // 1 win of 2 total closed trades
    }

    @Test
    void allWinsLeavesAverageLossNull() {
        when(viewService.list(userId)).thenReturn(List.of(sell(tcs, "TCS", new BigDecimal("100"))));

        OutcomeSummaryDto summary = service.compute(userId);

        assertThat(summary.averageLoss()).isNull();
        assertThat(summary.lossCount()).isZero();
    }

    @Test
    void identifiesTheBestAndWorstTradeByRealizedPnl() {
        var mid = sell(tcs, "TCS", new BigDecimal("50"));
        var best = sell(tcs, "TCS", new BigDecimal("500"));
        var worst = sell(infy, "INFY", new BigDecimal("-300"));
        when(viewService.list(userId)).thenReturn(List.of(mid, best, worst));

        OutcomeSummaryDto summary = service.compute(userId);

        assertThat(summary.bestTrade()).isEqualTo(best);
        assertThat(summary.worstTrade()).isEqualTo(worst);
    }

    @Test
    void groupsByInstrumentSortedByRealizedPnlDescending() {
        when(viewService.list(userId)).thenReturn(List.of(
            sell(tcs, "TCS", new BigDecimal("100")),
            sell(tcs, "TCS", new BigDecimal("50")),
            sell(infy, "INFY", new BigDecimal("300"))
        ));

        OutcomeSummaryDto summary = service.compute(userId);

        assertThat(summary.byInstrument()).hasSize(2);
        assertThat(summary.byInstrument().get(0).symbol()).isEqualTo("INFY"); // 300 > 150
        assertThat(summary.byInstrument().get(0).realizedPnl()).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(summary.byInstrument().get(1).symbol()).isEqualTo("TCS");
        assertThat(summary.byInstrument().get(1).realizedPnl()).isEqualByComparingTo(new BigDecimal("150"));
        assertThat(summary.byInstrument().get(1).closedTradeCount()).isEqualTo(2);
    }

    private TradeJournalEntryDto sell(UUID instrumentId, String symbol, BigDecimal realizedPnl) {
        return new TradeJournalEntryDto(
            instrumentId, symbol, "SELL", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.valueOf(100),
            BigDecimal.TEN, realizedPnl, null, Instant.now()
        );
    }

    private TradeJournalEntryDto buy(UUID instrumentId, String symbol) {
        return new TradeJournalEntryDto(
            instrumentId, symbol, "BUY", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.valueOf(100),
            null, null, null, Instant.now()
        );
    }
}
