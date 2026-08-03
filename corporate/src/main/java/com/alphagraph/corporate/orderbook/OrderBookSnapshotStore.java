package com.alphagraph.corporate.orderbook;

import com.alphagraph.corporate.api.OrderBookSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the most recent prior snapshot (for growth-percent comparison) and writes new snapshots.
 * One row per (instrument, day) - a second run on the same day upserts rather than duplicating,
 * since nothing about the ledger changes mid-day faster than the daily scheduler runs anyway.
 */
@Component
class OrderBookSnapshotStore {

    private final JdbcTemplate jdbcTemplate;

    OrderBookSnapshotStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<Double> findMostRecentOrderBookValue(UUID instrumentId, LocalDate beforeDate) {
        List<Double> rows = jdbcTemplate.query(
            """
            SELECT current_order_book_crore FROM corporate.order_book_snapshots
            WHERE instrument_id = ? AND as_of_date < ?
            ORDER BY as_of_date DESC LIMIT 1
            """,
            (rs, rowNum) -> rs.getDouble("current_order_book_crore"),
            instrumentId, Date.valueOf(beforeDate)
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    void write(OrderBookSnapshot snapshot) {
        jdbcTemplate.update(
            """
            INSERT INTO corporate.order_book_snapshots (
                id, instrument_id, symbol, as_of_date, current_order_book_crore, order_book_growth_pct,
                execution_visibility_years, order_count, order_quality, quality_score, confidence,
                rule_set_version, computed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                current_order_book_crore = EXCLUDED.current_order_book_crore,
                order_book_growth_pct = EXCLUDED.order_book_growth_pct,
                execution_visibility_years = EXCLUDED.execution_visibility_years,
                order_count = EXCLUDED.order_count,
                order_quality = EXCLUDED.order_quality,
                quality_score = EXCLUDED.quality_score,
                confidence = EXCLUDED.confidence,
                rule_set_version = EXCLUDED.rule_set_version,
                computed_at = EXCLUDED.computed_at
            """,
            UUID.randomUUID(), snapshot.instrumentId(), snapshot.symbol(), Date.valueOf(snapshot.asOfDate()),
            snapshot.currentOrderBookCrore(), snapshot.orderBookGrowthPct(), snapshot.executionVisibilityYears(),
            snapshot.orderCount(), snapshot.orderQuality().name(), snapshot.qualityScore(), snapshot.confidence(),
            snapshot.ruleSetVersion(), java.sql.Timestamp.from(snapshot.computedAt())
        );
    }
}
