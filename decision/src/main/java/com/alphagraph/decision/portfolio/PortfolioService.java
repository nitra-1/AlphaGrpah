package com.alphagraph.decision.portfolio;

import com.alphagraph.decision.api.PortfolioHolding;
import com.alphagraph.decision.journal.TradeJournalService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the single global portfolio (Module 3.3) - current holdings only, weighted-average cost
 * basis. Not a transaction ledger itself: {@link #buy} and {@link #sell} mutate one position's
 * quantity/avgBuyPrice in place, but every successful call also auto-records the trade via
 * {@link TradeJournalService} (Module 3.8) - the journal is a byproduct of these two methods, not
 * a separate manual-entry surface, so a trade is never entered twice. Same
 * {@code Optional}-on-not-found shape as {@code decision.watchlist.WatchlistService}, for the
 * same reason (this module cannot depend on api.error.NotFoundException).
 */
@Component
public class PortfolioService {

    private final JdbcTemplate jdbcTemplate;
    private final PortfolioStore store;
    private final PortfolioReader reader;
    private final TradeJournalService tradeJournalService;

    public PortfolioService(JdbcTemplate jdbcTemplate, PortfolioStore store, PortfolioReader reader, TradeJournalService tradeJournalService) {
        this.jdbcTemplate = jdbcTemplate;
        this.store = store;
        this.reader = reader;
        this.tradeJournalService = tradeJournalService;
    }

    /**
     * Adds to a position, recalculating the weighted-average cost basis:
     * newAvgPrice = (existingQty * existingAvgPrice + buyQty * buyPrice) / (existingQty + buyQty).
     * Creates a new holding at exactly buyPrice if none exists yet. Records a BUY journal entry
     * on success; {@code rationale} is optional context for why the trade was made.
     *
     * @return empty if instrumentId doesn't exist in reference.instruments at all
     */
    public Optional<PortfolioHolding> buy(UUID userId, UUID instrumentId, BigDecimal quantity, BigDecimal price, String rationale) {
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Buy quantity must be positive");
        }
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("Buy price must be positive");
        }

        String symbol;
        try {
            symbol = jdbcTemplate.queryForObject("SELECT symbol FROM reference.instruments WHERE id = ?", String.class, instrumentId);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }

        Optional<PortfolioHolding> existing = reader.findByInstrument(userId, instrumentId);
        BigDecimal newQuantity;
        BigDecimal newAvgPrice;
        if (existing.isPresent()) {
            PortfolioHolding holding = existing.get();
            BigDecimal existingCost = holding.quantity().multiply(holding.avgBuyPrice());
            BigDecimal addedCost = quantity.multiply(price);
            newQuantity = holding.quantity().add(quantity);
            newAvgPrice = existingCost.add(addedCost).divide(newQuantity, 4, RoundingMode.HALF_UP);
        } else {
            newQuantity = quantity;
            newAvgPrice = price;
        }

        store.upsert(userId, instrumentId, symbol, newQuantity, newAvgPrice);
        tradeJournalService.recordBuy(userId, instrumentId, symbol, quantity, price, rationale);
        return reader.findByInstrument(userId, instrumentId);
    }

    /**
     * Reduces a position. The average cost basis is untouched by a sell (only future buys move
     * it) - a sell realizes a gain/loss against the existing basis, it doesn't change what
     * remains was "paid for". The holding is deleted once quantity reaches zero. Records a SELL
     * journal entry on success, with realized P&L computed against the cost basis captured
     * BEFORE this sell mutates it: realizedPnl = quantity * (price - costBasisAtSale).
     *
     * @return empty if there's no holding at all for this instrument
     * @throws IllegalArgumentException if quantity exceeds what's held, or price isn't positive
     */
    public Optional<PortfolioHolding> sell(UUID userId, UUID instrumentId, BigDecimal quantity, BigDecimal price, String rationale) {
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Sell quantity must be positive");
        }
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("Sell price must be positive");
        }

        Optional<PortfolioHolding> existing = reader.findByInstrument(userId, instrumentId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        PortfolioHolding holding = existing.get();
        if (quantity.compareTo(holding.quantity()) > 0) {
            throw new IllegalArgumentException(
                "Cannot sell " + quantity + " of " + holding.symbol() + " - only " + holding.quantity() + " held"
            );
        }

        BigDecimal costBasisAtSale = holding.avgBuyPrice();
        BigDecimal realizedPnl = quantity.multiply(price.subtract(costBasisAtSale));

        BigDecimal remaining = holding.quantity().subtract(quantity);
        Optional<PortfolioHolding> result;
        if (remaining.signum() == 0) {
            store.delete(userId, instrumentId);
            // Transient confirmation value only - quantity=0 is never actually persisted (the
            // CHECK constraint on portfolio_holdings.quantity forbids it), the row is deleted.
            result = Optional.of(new PortfolioHolding(holding.id(), instrumentId, holding.symbol(), BigDecimal.ZERO, holding.avgBuyPrice(), holding.updatedAt(), holding.createdAt()));
        } else {
            store.upsert(userId, instrumentId, holding.symbol(), remaining, holding.avgBuyPrice());
            result = reader.findByInstrument(userId, instrumentId);
        }

        tradeJournalService.recordSell(userId, instrumentId, holding.symbol(), quantity, price, costBasisAtSale, realizedPnl, rationale);
        return result;
    }

    public List<PortfolioHolding> list(UUID userId) {
        return reader.findAll(userId);
    }

    /**
     * Deletes a holding entirely - NOT a sale. No new journal entry, no realized P&L, no price
     * required. For correcting placeholder/test data (e.g. clearing out holdings entered while
     * exploring the platform before starting to track real trades) - since this position never
     * represented a real trade to begin with, every existing Trade Journal entry for this
     * instrument (the BUY(s) that created it, and any SELLs against it) is purged along with it,
     * so a deleted holding doesn't linger as orphaned journal history. A real exit that should
     * stay on the record must go through {@link #sell} instead, never this method.
     *
     * @return false if there's no holding at all for this instrument
     */
    public boolean remove(UUID userId, UUID instrumentId) {
        if (reader.findByInstrument(userId, instrumentId).isEmpty()) {
            return false;
        }
        store.delete(userId, instrumentId);
        tradeJournalService.deleteAllForInstrument(userId, instrumentId);
        return true;
    }
}
