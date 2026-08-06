package com.alphagraph.corporate.orderbook;

import com.alphagraph.corporate.api.OrderBookEntry;
import com.alphagraph.corporate.api.OrderLifecycleStage;
import com.alphagraph.corporate.api.OrderRecurrence;
import com.alphagraph.corporate.api.OrderScope;
import com.alphagraph.corporate.api.OrderSector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads ledger entries - the input to {@link OrderBookAggregationEngine}. Public since Module 2.9:
 * the AI Analyst needs individual order-lifecycle events (e.g. "a real ₹2,800 Cr order from the
 * Ministry of Defence"), not just {@code OrderBookSnapshotReader}'s aggregated totals.
 * {@link #findRecentAcrossAllInstruments} was added in Module 2.10 (Decision Intelligence API
 * Layer) - a "Today's Biggest Orders" dashboard widget needs to rank across the whole tracked
 * universe, not one instrument at a time like every prior consumer.
 */
@Component
public class OrderBookLedgerReader {

    static final String CONSUMER = "ORDER_BOOK_ENGINE";

    private static final RowMapper<OrderBookEntry> ROW_MAPPER = (rs, rowNum) -> new OrderBookEntry(
        (UUID) rs.getObject("id"), (UUID) rs.getObject("document_id"), (UUID) rs.getObject("instrument_id"),
        rs.getString("symbol"), (UUID) rs.getObject("customer_entity_id"),
        rs.getObject("order_value_crore") == null ? null : rs.getDouble("order_value_crore"),
        rs.getString("business_unit"), rs.getString("execution_start"), rs.getString("execution_end"),
        rs.getString("order_scope") == null ? null : OrderScope.valueOf(rs.getString("order_scope")),
        rs.getString("order_sector") == null ? null : OrderSector.valueOf(rs.getString("order_sector")),
        rs.getString("order_recurrence") == null ? null : OrderRecurrence.valueOf(rs.getString("order_recurrence")),
        OrderLifecycleStage.valueOf(rs.getString("lifecycle_stage")),
        rs.getTimestamp("detected_at").toInstant()
    );

    private static final String SELECT_COLUMNS = """
        id, document_id, instrument_id, symbol, customer_entity_id, order_value_crore, business_unit,
        execution_start, execution_end, order_scope, order_sector, order_recurrence, lifecycle_stage, detected_at
        """;

    private final JdbcTemplate jdbcTemplate;

    OrderBookLedgerReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<UUID> findDistinctInstrumentIds() {
        return jdbcTemplate.query(
            "SELECT DISTINCT instrument_id FROM corporate.order_book_ledger",
            (rs, rowNum) -> (UUID) rs.getObject("instrument_id")
        );
    }

    public List<OrderBookEntry> findByInstrument(UUID instrumentId) {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM corporate.order_book_ledger WHERE instrument_id = ?",
            ROW_MAPPER, instrumentId
        );
    }

    /** Every NEW_ORDER/TENDER_WIN entry across all instruments in the last {@code lookbackDays}, largest value first. Nulls (no disclosed value) sort last. */
    public List<OrderBookEntry> findRecentAcrossAllInstruments(int lookbackDays) {
        Instant since = Instant.now().minusSeconds(lookbackDays * 86400L);
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + """
             FROM corporate.order_book_ledger
             WHERE detected_at >= ? AND lifecycle_stage IN ('NEW_ORDER', 'TENDER_WIN')
             ORDER BY order_value_crore DESC NULLS LAST
            """,
            ROW_MAPPER, Timestamp.from(since)
        );
    }
}
