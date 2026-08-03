package com.alphagraph.corporate.orderbook;

import com.alphagraph.corporate.api.OrderSignal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Replaces one instrument's signal set on every run, rather than appending - signals reflect
 * current state ("is this true right now"), so re-running detection over the same ledger and
 * inserting fresh rows without clearing old ones would duplicate the same signal (e.g.
 * EXECUTION_DELAY on an order that's still overdue) every single day forever.
 */
@Component
class OrderBookSignalWriter {

    private final JdbcTemplate jdbcTemplate;

    OrderBookSignalWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void replaceAll(UUID instrumentId, List<OrderSignal> signals) {
        jdbcTemplate.update("DELETE FROM corporate.order_book_signals WHERE instrument_id = ?", instrumentId);

        for (OrderSignal signal : signals) {
            jdbcTemplate.update(
                """
                INSERT INTO corporate.order_book_signals (id, instrument_id, symbol, signal_type, related_order_id, detail, detected_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), signal.instrumentId(), signal.symbol(), signal.signalType().name(),
                signal.relatedOrderId(), signal.detail(), Timestamp.from(signal.detectedAt())
            );
        }
    }
}
