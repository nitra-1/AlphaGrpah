package com.alphagraph.decision.journal;

import com.alphagraph.decision.api.TradeAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Raw insert for decision.trade_journal_entries - append-only, no update/delete, matching the table's immutable design. */
@Component
class TradeJournalStore {

    private final JdbcTemplate jdbcTemplate;

    TradeJournalStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(
        UUID instrumentId, String symbol, TradeAction action, BigDecimal quantity, BigDecimal price,
        BigDecimal costBasisPrice, BigDecimal realizedPnl, String rationale
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO decision.trade_journal_entries (
                id, instrument_id, symbol, action, quantity, price, cost_basis_price, realized_pnl, rationale
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(), instrumentId, symbol, action.name(), quantity, price, costBasisPrice, realizedPnl, rationale
        );
    }
}
