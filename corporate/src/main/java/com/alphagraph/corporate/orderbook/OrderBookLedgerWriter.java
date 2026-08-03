package com.alphagraph.corporate.orderbook;

import com.alphagraph.corporate.api.OrderBookEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/** Persists one {@link OrderBookEntry} as a {@code corporate.order_book_ledger} row. */
@Component
class OrderBookLedgerWriter {

    private final JdbcTemplate jdbcTemplate;

    OrderBookLedgerWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(OrderBookEntry entry) {
        jdbcTemplate.update(
            """
            INSERT INTO corporate.order_book_ledger (
                id, document_id, instrument_id, symbol, customer, order_value_crore, business_unit,
                execution_start, execution_end, order_scope, order_sector, order_recurrence,
                lifecycle_stage, detected_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            entry.id(), entry.documentId(), entry.instrumentId(), entry.symbol(),
            entry.customer(), entry.orderValueCrore(), entry.businessUnit(),
            entry.executionStart(), entry.executionEnd(),
            entry.orderScope() == null ? null : entry.orderScope().name(),
            entry.orderSector() == null ? null : entry.orderSector().name(),
            entry.orderRecurrence() == null ? null : entry.orderRecurrence().name(),
            entry.lifecycleStage().name(), Timestamp.from(entry.detectedAt())
        );
    }
}
