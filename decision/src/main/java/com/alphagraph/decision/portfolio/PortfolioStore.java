package com.alphagraph.decision.portfolio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Raw persistence for decision.portfolio_holdings - no averaging/validation logic, that's {@link PortfolioService}'s job. */
@Component
class PortfolioStore {

    private final JdbcTemplate jdbcTemplate;

    PortfolioStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void upsert(UUID instrumentId, String symbol, BigDecimal quantity, BigDecimal avgBuyPrice) {
        jdbcTemplate.update(
            """
            INSERT INTO decision.portfolio_holdings (id, instrument_id, symbol, quantity, avg_buy_price)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id) DO UPDATE SET
                quantity = EXCLUDED.quantity,
                avg_buy_price = EXCLUDED.avg_buy_price
            """,
            UUID.randomUUID(), instrumentId, symbol, quantity, avgBuyPrice
        );
    }

    void delete(UUID instrumentId) {
        jdbcTemplate.update("DELETE FROM decision.portfolio_holdings WHERE instrument_id = ?", instrumentId);
    }
}
