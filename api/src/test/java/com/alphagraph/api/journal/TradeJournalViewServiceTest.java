package com.alphagraph.api.journal;

import com.alphagraph.decision.api.TradeAction;
import com.alphagraph.decision.api.TradeJournalEntry;
import com.alphagraph.decision.journal.TradeJournalService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeJournalViewServiceTest {

    private final TradeJournalService tradeJournalService = mock(TradeJournalService.class);
    private final TradeJournalViewService viewService = new TradeJournalViewService(tradeJournalService);

    private final UUID instrumentId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void buyEntryHasNoCostBasisOrRealizedPnlAndComputesTradeValue() {
        TradeJournalEntry entry = new TradeJournalEntry(
            UUID.randomUUID(), instrumentId, "TCS", TradeAction.BUY,
            new BigDecimal("10"), new BigDecimal("100"), null, null, "Initial position", Instant.now()
        );
        when(tradeJournalService.list(userId)).thenReturn(List.of(entry));

        TradeJournalEntryDto dto = viewService.list(userId).get(0);

        assertThat(dto.action()).isEqualTo("BUY");
        assertThat(dto.tradeValue()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(dto.costBasisPrice()).isNull();
        assertThat(dto.realizedPnl()).isNull();
        assertThat(dto.rationale()).isEqualTo("Initial position");
    }

    @Test
    void sellEntryCarriesCostBasisAndRealizedPnlThrough() {
        TradeJournalEntry entry = new TradeJournalEntry(
            UUID.randomUUID(), instrumentId, "TCS", TradeAction.SELL,
            new BigDecimal("6"), new BigDecimal("120"), new BigDecimal("100"), new BigDecimal("120"), null, Instant.now()
        );
        when(tradeJournalService.listByInstrument(userId, instrumentId)).thenReturn(List.of(entry));

        TradeJournalEntryDto dto = viewService.listByInstrument(userId, instrumentId).get(0);

        assertThat(dto.action()).isEqualTo("SELL");
        assertThat(dto.tradeValue()).isEqualByComparingTo(new BigDecimal("720"));
        assertThat(dto.costBasisPrice()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(dto.realizedPnl()).isEqualByComparingTo(new BigDecimal("120"));
    }
}
