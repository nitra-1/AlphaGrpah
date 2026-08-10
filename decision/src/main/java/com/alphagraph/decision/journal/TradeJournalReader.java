package com.alphagraph.decision.journal;

import com.alphagraph.decision.api.TradeAction;
import com.alphagraph.decision.api.TradeJournalEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class TradeJournalReader {

    private static final RowMapper<TradeJournalEntry> ROW_MAPPER = (rs, rowNum) -> new TradeJournalEntry(
        (UUID) rs.getObject("id"), (UUID) rs.getObject("instrument_id"), rs.getString("symbol"),
        TradeAction.valueOf(rs.getString("action")), rs.getBigDecimal("quantity"), rs.getBigDecimal("price"),
        rs.getBigDecimal("cost_basis_price"), rs.getBigDecimal("realized_pnl"), rs.getString("rationale"),
        rs.getTimestamp("created_at").toInstant()
    );

    private static final String SELECT_COLUMNS = """
        id, instrument_id, symbol, action, quantity, price, cost_basis_price, realized_pnl, rationale, created_at
        """;

    private final JdbcTemplate jdbcTemplate;

    TradeJournalReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Every trade ever recorded for this user, newest first. */
    List<TradeJournalEntry> findAll(UUID userId) {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM decision.trade_journal_entries WHERE user_id = ? ORDER BY created_at DESC",
            ROW_MAPPER, userId
        );
    }

    /** Every trade recorded for one instrument by this user, newest first. */
    List<TradeJournalEntry> findByInstrument(UUID userId, UUID instrumentId) {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM decision.trade_journal_entries WHERE user_id = ? AND instrument_id = ? ORDER BY created_at DESC",
            ROW_MAPPER, userId, instrumentId
        );
    }
}
