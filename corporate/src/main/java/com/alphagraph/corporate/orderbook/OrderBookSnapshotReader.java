package com.alphagraph.corporate.orderbook;

import com.alphagraph.corporate.api.OrderBookSnapshot;
import com.alphagraph.corporate.api.OrderQuality;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads an instrument's latest {@link OrderBookSnapshot} back - added in Module 2.8 (Corporate
 * Signal Engine), the first consumer that needs a full snapshot read rather than just the single
 * value {@link OrderBookSnapshotStore#findMostRecentOrderBookValue} already exposes internally.
 */
@Component
public class OrderBookSnapshotReader {

    private final JdbcTemplate jdbcTemplate;

    public OrderBookSnapshotReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<OrderBookSnapshot> findLatest(UUID instrumentId) {
        List<OrderBookSnapshot> rows = jdbcTemplate.query(
            """
            SELECT instrument_id, symbol, as_of_date, current_order_book_crore, order_book_growth_pct,
                   execution_visibility_years, order_count, order_quality, quality_score, confidence,
                   rule_set_version, computed_at
            FROM corporate.order_book_snapshots WHERE instrument_id = ? ORDER BY as_of_date DESC LIMIT 1
            """,
            (rs, rowNum) -> new OrderBookSnapshot(
                (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
                rs.getDouble("current_order_book_crore"),
                rs.getObject("order_book_growth_pct") == null ? null : rs.getDouble("order_book_growth_pct"),
                rs.getDouble("execution_visibility_years"), rs.getInt("order_count"),
                OrderQuality.valueOf(rs.getString("order_quality")), rs.getDouble("quality_score"), rs.getDouble("confidence"),
                rs.getInt("rule_set_version"), rs.getTimestamp("computed_at").toInstant()
            ),
            instrumentId
        );
        return rows.stream().findFirst();
    }
}
