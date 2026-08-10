package com.alphagraph.decision.journal;

import com.alphagraph.decision.api.TradeAction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TradeJournalServiceTest {

    private final TradeJournalStore store = mock(TradeJournalStore.class);
    private final TradeJournalReader reader = mock(TradeJournalReader.class);
    private final TradeJournalService service = new TradeJournalService(store, reader);

    private final UUID instrumentId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void recordBuyInsertsABuyRowWithNoCostBasisOrRealizedPnl() {
        service.recordBuy(userId, instrumentId, "TCS", new BigDecimal("10"), new BigDecimal("100"), "Initial position");

        verify(store).insert(userId, instrumentId, "TCS", TradeAction.BUY, new BigDecimal("10"), new BigDecimal("100"), null, null, "Initial position");
    }

    @Test
    void recordSellInsertsASellRowCarryingCostBasisAndRealizedPnl() {
        service.recordSell(userId, instrumentId, "TCS", new BigDecimal("6"), new BigDecimal("120"), new BigDecimal("100"), new BigDecimal("120"), null);

        verify(store).insert(userId, instrumentId, "TCS", TradeAction.SELL, new BigDecimal("6"), new BigDecimal("120"), new BigDecimal("100"), new BigDecimal("120"), null);
    }

    @Test
    void listDelegatesToTheReader() {
        service.list(userId);
        verify(reader).findAll(userId);
    }

    @Test
    void listByInstrumentDelegatesToTheReader() {
        service.listByInstrument(userId, instrumentId);
        verify(reader).findByInstrument(userId, instrumentId);
    }
}
