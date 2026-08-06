package com.alphagraph.decision.portfolio;

import com.alphagraph.decision.api.PortfolioHolding;
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
 * basis. Not a transaction ledger: {@link #buy} and {@link #sell} mutate one position's
 * quantity/avgBuyPrice in place, they don't record the individual event anywhere - that's Module
 * 3.8's (Trade Journal) job. Same {@code Optional}-on-not-found shape as
 * {@code decision.watchlist.WatchlistService}, for the same reason (this module cannot depend on
 * api.error.NotFoundException).
 */
@Component
public class PortfolioService {

    private final JdbcTemplate jdbcTemplate;
    private final PortfolioStore store;
    private final PortfolioReader reader;

    public PortfolioService(JdbcTemplate jdbcTemplate, PortfolioStore store, PortfolioReader reader) {
        this.jdbcTemplate = jdbcTemplate;
        this.store = store;
        this.reader = reader;
    }

    /**
     * Adds to a position, recalculating the weighted-average cost basis:
     * newAvgPrice = (existingQty * existingAvgPrice + buyQty * buyPrice) / (existingQty + buyQty).
     * Creates a new holding at exactly buyPrice if none exists yet.
     *
     * @return empty if instrumentId doesn't exist in reference.instruments at all
     */
    public Optional<PortfolioHolding> buy(UUID instrumentId, BigDecimal quantity, BigDecimal price) {
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

        Optional<PortfolioHolding> existing = reader.findByInstrument(instrumentId);
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

        store.upsert(instrumentId, symbol, newQuantity, newAvgPrice);
        return reader.findByInstrument(instrumentId);
    }

    /**
     * Reduces a position. The average cost basis is untouched by a sell (only future buys move
     * it) - a sell realizes a gain/loss against the existing basis, it doesn't change what
     * remains was "paid for". The holding is deleted once quantity reaches zero.
     *
     * @return empty if there's no holding at all for this instrument
     * @throws IllegalArgumentException if quantity exceeds what's held
     */
    public Optional<PortfolioHolding> sell(UUID instrumentId, BigDecimal quantity) {
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Sell quantity must be positive");
        }

        Optional<PortfolioHolding> existing = reader.findByInstrument(instrumentId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        PortfolioHolding holding = existing.get();
        if (quantity.compareTo(holding.quantity()) > 0) {
            throw new IllegalArgumentException(
                "Cannot sell " + quantity + " of " + holding.symbol() + " - only " + holding.quantity() + " held"
            );
        }

        BigDecimal remaining = holding.quantity().subtract(quantity);
        if (remaining.signum() == 0) {
            store.delete(instrumentId);
            // Transient confirmation value only - quantity=0 is never actually persisted (the
            // CHECK constraint on portfolio_holdings.quantity forbids it), the row is deleted.
            return Optional.of(new PortfolioHolding(holding.id(), instrumentId, holding.symbol(), BigDecimal.ZERO, holding.avgBuyPrice(), holding.updatedAt()));
        }
        store.upsert(instrumentId, holding.symbol(), remaining, holding.avgBuyPrice());
        return reader.findByInstrument(instrumentId);
    }

    public List<PortfolioHolding> list() {
        return reader.findAll();
    }
}
