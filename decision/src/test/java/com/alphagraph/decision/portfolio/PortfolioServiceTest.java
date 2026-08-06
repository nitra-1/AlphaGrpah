package com.alphagraph.decision.portfolio;

import com.alphagraph.decision.api.PortfolioHolding;
import com.alphagraph.decision.journal.TradeJournalService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortfolioServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PortfolioStore store = mock(PortfolioStore.class);
    private final PortfolioReader reader = mock(PortfolioReader.class);
    private final TradeJournalService tradeJournalService = mock(TradeJournalService.class);
    private final PortfolioService service = new PortfolioService(jdbcTemplate, store, reader, tradeJournalService);

    private final UUID instrumentId = UUID.randomUUID();

    @Test
    void buyingIntoAnEmptyPositionSetsQuantityAndPriceDirectly() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class), eq(instrumentId))).thenReturn("TCS");
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.empty())
            .thenReturn(Optional.of(holding(new BigDecimal("10"), new BigDecimal("100"))));

        Optional<PortfolioHolding> result = service.buy(instrumentId, new BigDecimal("10"), new BigDecimal("100"), "Initial position");

        assertThat(result).isPresent();
        verify(store).upsert(eq(instrumentId), eq("TCS"), eq(new BigDecimal("10")), eq(new BigDecimal("100")));
    }

    @Test
    void buyingMoreRecalculatesTheWeightedAverageCostBasis() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class), eq(instrumentId))).thenReturn("TCS");
        // existing: 10 @ 100 (cost 1000). Buying 10 @ 200 (cost 2000). New: 20 @ (3000/20 = 150).
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.of(holding(new BigDecimal("10"), new BigDecimal("100"))))
            .thenReturn(Optional.of(holding(new BigDecimal("20"), new BigDecimal("150"))));

        service.buy(instrumentId, new BigDecimal("10"), new BigDecimal("200"), null);

        verify(store).upsert(eq(instrumentId), eq("TCS"), eq(new BigDecimal("20")), eq(new BigDecimal("150.0000")));
    }

    @Test
    void successfulBuyRecordsABuyJournalEntryWithTheRationale() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class), eq(instrumentId))).thenReturn("TCS");
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.empty())
            .thenReturn(Optional.of(holding(new BigDecimal("10"), new BigDecimal("100"))));

        service.buy(instrumentId, new BigDecimal("10"), new BigDecimal("100"), "Strong earnings beat");

        verify(tradeJournalService).recordBuy(instrumentId, "TCS", new BigDecimal("10"), new BigDecimal("100"), "Strong earnings beat");
    }

    @Test
    void buyWithNonPositiveQuantityThrowsAndNeverTouchesTheJournal() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.buy(instrumentId, BigDecimal.ZERO, new BigDecimal("100"), null));
        verifyNoInteractions(tradeJournalService);
    }

    @Test
    void buyWithNonPositivePriceThrows() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.buy(instrumentId, new BigDecimal("10"), BigDecimal.ZERO, null));
    }

    @Test
    void buyForANonexistentInstrumentReturnsEmptyWithoutTouchingTheStoreOrJournal() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class), eq(instrumentId)))
            .thenThrow(new EmptyResultDataAccessException(1));

        Optional<PortfolioHolding> result = service.buy(instrumentId, new BigDecimal("10"), new BigDecimal("100"), null);

        assertThat(result).isEmpty();
        verify(store, never()).upsert(any(), any(), any(), any());
        verifyNoInteractions(tradeJournalService);
    }

    @Test
    void sellingPartOfAPositionReducesQuantityWithoutChangingAvgPrice() {
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.of(holding(new BigDecimal("10"), new BigDecimal("100"))))
            .thenReturn(Optional.of(holding(new BigDecimal("4"), new BigDecimal("100"))));

        service.sell(instrumentId, new BigDecimal("6"), new BigDecimal("120"), null);

        verify(store).upsert(eq(instrumentId), eq("TCS"), eq(new BigDecimal("4")), eq(new BigDecimal("100")));
    }

    @Test
    void sellRecordsRealizedPnlAgainstTheCostBasisCapturedBeforeTheMutation() {
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.of(holding(new BigDecimal("10"), new BigDecimal("100"))))
            .thenReturn(Optional.of(holding(new BigDecimal("4"), new BigDecimal("100"))));

        // Sell 6 @ 120, cost basis 100 -> realized = 6 * (120 - 100) = 120.
        service.sell(instrumentId, new BigDecimal("6"), new BigDecimal("120"), "Taking partial profit");

        verify(tradeJournalService).recordSell(
            instrumentId, "TCS", new BigDecimal("6"), new BigDecimal("120"),
            new BigDecimal("100"), new BigDecimal("120"), "Taking partial profit"
        );
    }

    @Test
    void sellAtALossRecordsANegativeRealizedPnl() {
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.of(holding(new BigDecimal("10"), new BigDecimal("100"))))
            .thenReturn(Optional.of(holding(new BigDecimal("4"), new BigDecimal("100"))));

        // Sell 6 @ 80, cost basis 100 -> realized = 6 * (80 - 100) = -120.
        service.sell(instrumentId, new BigDecimal("6"), new BigDecimal("80"), null);

        verify(tradeJournalService).recordSell(
            eq(instrumentId), eq("TCS"), eq(new BigDecimal("6")), eq(new BigDecimal("80")),
            eq(new BigDecimal("100")), eq(new BigDecimal("-120")), isNull()
        );
    }

    @Test
    void sellingTheEntirePositionDeletesTheHoldingAndStillRecordsTheJournalEntry() {
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.of(holding(new BigDecimal("10"), new BigDecimal("100"))));

        Optional<PortfolioHolding> result = service.sell(instrumentId, new BigDecimal("10"), new BigDecimal("150"), null);

        verify(store).delete(instrumentId);
        verify(store, never()).upsert(any(), any(), any(), any());
        assertThat(result).isPresent();
        assertThat(result.get().quantity()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(tradeJournalService).recordSell(
            instrumentId, "TCS", new BigDecimal("10"), new BigDecimal("150"), new BigDecimal("100"), new BigDecimal("500"), null
        );
    }

    @Test
    void sellingMoreThanHeldThrowsAndTouchesNeitherStoreNorJournal() {
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.of(holding(new BigDecimal("10"), new BigDecimal("100"))));

        assertThatIllegalArgumentException().isThrownBy(() -> service.sell(instrumentId, new BigDecimal("11"), new BigDecimal("100"), null));
        verify(store, never()).upsert(any(), any(), any(), any());
        verify(store, never()).delete(any());
        verifyNoInteractions(tradeJournalService);
    }

    @Test
    void sellWithNoExistingHoldingReturnsEmptyWithoutTouchingTheJournal() {
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.empty());

        assertThat(service.sell(instrumentId, new BigDecimal("1"), new BigDecimal("100"), null)).isEmpty();
        verifyNoInteractions(tradeJournalService);
    }

    @Test
    void sellWithNonPositiveQuantityThrows() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.sell(instrumentId, BigDecimal.ZERO, new BigDecimal("100"), null));
    }

    @Test
    void sellWithNonPositivePriceThrows() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.sell(instrumentId, new BigDecimal("1"), BigDecimal.ZERO, null));
    }

    private PortfolioHolding holding(BigDecimal quantity, BigDecimal avgPrice) {
        return new PortfolioHolding(UUID.randomUUID(), instrumentId, "TCS", quantity, avgPrice, Instant.now());
    }
}
