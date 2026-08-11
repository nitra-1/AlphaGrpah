package com.alphagraph.decision.journal;

import com.alphagraph.decision.api.TradeAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Raw insert/delete for decision.trade_journal_entries - append-only in the ordinary course of
 * trading (no update, no delete of an individual entry), except for
 * {@link #deleteByInstrument}, which exists solely so {@code PortfolioService.remove} can purge a
 * placeholder position's entries along with it.
 */
@Component
class TradeJournalStore {

    private final JdbcTemplate jdbcTemplate;

    TradeJournalStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(
        UUID userId, UUID instrumentId, String symbol, TradeAction action, BigDecimal quantity, BigDecimal price,
        BigDecimal costBasisPrice, BigDecimal realizedPnl, String rationale
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO decision.trade_journal_entries (
                id, user_id, instrument_id, symbol, action, quantity, price, cost_basis_price, realized_pnl, rationale
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(), userId, instrumentId, symbol, action.name(), quantity, price, costBasisPrice, realizedPnl, rationale
        );
    }

    void deleteByInstrument(UUID userId, UUID instrumentId) {
        jdbcTemplate.update(
            "DELETE FROM decision.trade_journal_entries WHERE user_id = ? AND instrument_id = ?", userId, instrumentId
        );
    }
}
