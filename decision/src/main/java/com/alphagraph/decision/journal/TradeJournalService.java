package com.alphagraph.decision.journal;

import com.alphagraph.decision.api.TradeAction;
import com.alphagraph.decision.api.TradeJournalEntry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The Trade Journal's only entry point (Module 3.8) - {@code recordBuy}/{@code recordSell} are
 * called exclusively by {@code decision.portfolio.PortfolioService} right after a trade actually
 * executes (never for a no-op, a validation failure, or an unresolvable instrument - those never
 * reach the store). {@code list}/{@code listByInstrument} are what the api layer reads back;
 * there is no write path from outside {@code decision.portfolio} by design - a trade journal
 * entry is a record of what happened, not something entered independently of the trade itself.
 */
@Component
public class TradeJournalService {

    private final TradeJournalStore store;
    private final TradeJournalReader reader;

    public TradeJournalService(TradeJournalStore store, TradeJournalReader reader) {
        this.store = store;
        this.reader = reader;
    }

    public void recordBuy(UUID userId, UUID instrumentId, String symbol, BigDecimal quantity, BigDecimal price, String rationale) {
        store.insert(userId, instrumentId, symbol, TradeAction.BUY, quantity, price, null, null, rationale);
    }

    public void recordSell(
        UUID userId, UUID instrumentId, String symbol, BigDecimal quantity, BigDecimal price,
        BigDecimal costBasisPrice, BigDecimal realizedPnl, String rationale
    ) {
        store.insert(userId, instrumentId, symbol, TradeAction.SELL, quantity, price, costBasisPrice, realizedPnl, rationale);
    }

    public List<TradeJournalEntry> list(UUID userId) {
        return reader.findAll(userId);
    }

    public List<TradeJournalEntry> listByInstrument(UUID userId, UUID instrumentId) {
        return reader.findByInstrument(userId, instrumentId);
    }
}
