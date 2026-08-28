package com.alphagraph.market.pricing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds symbols still under active Discovery review (raw cross-schema read of
 * {@code ownership.discovery_status}, same established by-value pattern as
 * {@link DiscoveryCandidateLookup}) that don't yet have {@code targetRows} of real trading
 * history in {@code market.discovered_prices} - the driving set for
 * {@link MarketPriceBackfillOrchestrator}'s requirement-driven walk-backward backfill.
 */
@Component
class BackfillCandidateReader {

    private final JdbcTemplate jdbcTemplate;

    BackfillCandidateReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Symbols not DISMISSED and not already promoted, whose row count in
     * {@code discovered_prices} is below {@code targetRows}. "Not DISMISSED" alone isn't enough
     * to exclude promoted symbols - {@code ownership.discovery_status.status} never actually gets
     * written as {@code PROMOTED} (promotion is detected live by a symbol's presence in
     * {@code reference.instruments}, per {@code ownership.deals.DiscoveryReader}'s own doc
     * comment), so a promoted symbol would otherwise still carry whatever status it had before
     * promotion and get needlessly backfilled here even though it now gets real history through
     * the normal tracked-instrument pipeline. Mirrors DiscoveryReader's exact exclusion.
     */
    List<String> findSymbolsNeedingBackfill(int targetRows) {
        return jdbcTemplate.query(
            """
            SELECT ds.symbol
            FROM ownership.discovery_status ds
            LEFT JOIN (
                SELECT symbol, COUNT(*) AS row_count FROM market.discovered_prices GROUP BY symbol
            ) dp ON dp.symbol = ds.symbol
            WHERE ds.status != 'DISMISSED'
            AND NOT EXISTS (SELECT 1 FROM reference.instruments i WHERE i.symbol = ds.symbol)
            AND COALESCE(dp.row_count, 0) < ?
            """,
            (rs, rowNum) -> rs.getString("symbol"), targetRows
        );
    }

    /**
     * Every distinct trade_date already captured for the given symbols, keyed by symbol - lets
     * the orchestrator track true distinct-row counts as it walks (an upsert on a date it already
     * has, e.g. from the prior day's normal gated capture, must not be double-counted as new
     * progress toward {@code targetRows}).
     */
    Map<String, Set<LocalDate>> findExistingTradeDates(List<String> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(symbols.size(), "?"));
        List<Object[]> rows = jdbcTemplate.query(
            "SELECT symbol, trade_date FROM market.discovered_prices WHERE symbol IN (" + placeholders + ")",
            (rs, rowNum) -> new Object[] {rs.getString("symbol"), rs.getDate("trade_date").toLocalDate()},
            symbols.toArray()
        );
        Map<String, Set<LocalDate>> result = new HashMap<>();
        for (Object[] row : rows) {
            result.computeIfAbsent((String) row[0], key -> new HashSet<>()).add((LocalDate) row[1]);
        }
        return result;
    }
}
