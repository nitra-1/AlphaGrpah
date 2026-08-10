package com.alphagraph.api.journal;

import com.alphagraph.decision.api.TradeJournalEntry;
import com.alphagraph.decision.journal.TradeJournalService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TradeJournalViewService {

    private final TradeJournalService tradeJournalService;

    public TradeJournalViewService(TradeJournalService tradeJournalService) {
        this.tradeJournalService = tradeJournalService;
    }

    public List<TradeJournalEntryDto> list(UUID userId) {
        return tradeJournalService.list(userId).stream().map(TradeJournalViewService::toDto).toList();
    }

    public List<TradeJournalEntryDto> listByInstrument(UUID userId, UUID instrumentId) {
        return tradeJournalService.listByInstrument(userId, instrumentId).stream().map(TradeJournalViewService::toDto).toList();
    }

    private static TradeJournalEntryDto toDto(TradeJournalEntry e) {
        return new TradeJournalEntryDto(
            e.instrumentId(), e.symbol(), e.action().name(),
            e.quantity(), e.price(), e.quantity().multiply(e.price()),
            e.costBasisPrice(), e.realizedPnl(),
            e.rationale(), e.createdAt()
        );
    }
}
