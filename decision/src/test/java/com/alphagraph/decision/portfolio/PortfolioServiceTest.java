package com.alphagraph.decision.portfolio;

import com.alphagraph.decision.api.PortfolioHolding;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PortfolioStore store = mock(PortfolioStore.class);
    private final PortfolioReader reader = mock(PortfolioReader.class);
    private final PortfolioService service = new PortfolioService(jdbcTemplate, store, reader);

    private final UUID instrumentId = UUID.randomUUID();

    @Test
    void buyingIntoAnEmptyPositionSetsQuantityAndPriceDirectly() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class), eq(instrumentId))).thenReturn("TCS");
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.empty())
            .thenReturn(Optional.of(holding(new BigDecimal("10"), new BigDecimal("100"))));

        Optional<PortfolioHolding> result = service.buy(instrumentId, new BigDecimal("10"), new BigDecimal("100"));

        assertThat(result).isPresent();
        verify(store).upsert(eq(instrumentId), eq("TCS"), eq(new BigDecimal("10")), eq(new BigDecimal("100")));
    }

    @Test
    void buyingMoreRecalculatesTheWeightedAverageCostBasis() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class), eq(instrumentId))).thenReturn("TCS");
        // existing: 10 @ 100 (cost 1000). Buying 10 @ 200 (cost 2000). New: 20 @ (3000/20 = 150).
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.of(holding(new BigDecimal("10"), new BigDecimal("100"))))
            .thenReturn(Optional.of(holding(new BigDecimal("20"), new BigDecimal("150"))));

        service.buy(instrumentId, new BigDecimal("10"), new BigDecimal("200"));

        verify(store).upsert(eq(instrumentId), eq("TCS"), eq(new BigDecimal("20")), eq(new BigDecimal("150.0000")));
    }

    @Test
    void buyWithNonPositiveQuantityThrows() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.buy(instrumentId, BigDecimal.ZERO, new BigDecimal("100")));
    }

    @Test
    void buyWithNonPositivePriceThrows() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.buy(instrumentId, new BigDecimal("10"), BigDecimal.ZERO));
    }

    @Test
    void buyForANonexistentInstrumentReturnsEmptyWithoutTouchingTheStore() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class), eq(instrumentId)))
            .thenThrow(new EmptyResultDataAccessException(1));

        Optional<PortfolioHolding> result = service.buy(instrumentId, new BigDecimal("10"), new BigDecimal("100"));

        assertThat(result).isEmpty();
        verify(store, never()).upsert(any(), any(), any(), any());
    }

    @Test
    void sellingPartOfAPositionReducesQuantityWithoutChangingAvgPrice() {
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.of(holding(new BigDecimal("10"), new BigDecimal("100"))))
            .thenReturn(Optional.of(holding(new BigDecimal("4"), new BigDecimal("100"))));

        service.sell(instrumentId, new BigDecimal("6"));

        verify(store).upsert(eq(instrumentId), eq("TCS"), eq(new BigDecimal("4")), eq(new BigDecimal("100")));
    }

    @Test
    void sellingTheEntirePositionDeletesTheHolding() {
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.of(holding(new BigDecimal("10"), new BigDecimal("100"))));

        Optional<PortfolioHolding> result = service.sell(instrumentId, new BigDecimal("10"));

        verify(store).delete(instrumentId);
        verify(store, never()).upsert(any(), any(), any(), any());
        assertThat(result).isPresent();
        assertThat(result.get().quantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sellingMoreThanHeldThrowsAndTouchesNeitherStoreMethod() {
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.of(holding(new BigDecimal("10"), new BigDecimal("100"))));

        assertThatIllegalArgumentException().isThrownBy(() -> service.sell(instrumentId, new BigDecimal("11")));
        verify(store, never()).upsert(any(), any(), any(), any());
        verify(store, never()).delete(any());
    }

    @Test
    void sellWithNoExistingHoldingReturnsEmpty() {
        when(reader.findByInstrument(instrumentId)).thenReturn(Optional.empty());

        assertThat(service.sell(instrumentId, new BigDecimal("1"))).isEmpty();
    }

    @Test
    void sellWithNonPositiveQuantityThrows() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.sell(instrumentId, BigDecimal.ZERO));
    }

    private PortfolioHolding holding(BigDecimal quantity, BigDecimal avgPrice) {
        return new PortfolioHolding(UUID.randomUUID(), instrumentId, "TCS", quantity, avgPrice, Instant.now());
    }
}
