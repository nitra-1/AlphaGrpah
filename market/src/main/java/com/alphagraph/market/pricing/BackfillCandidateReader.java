package com.alphagraph.market.pricing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
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
 * history *before the date they actually need it* - the driving set for
 * {@link MarketPriceBackfillOrchestrator}'s requirement-driven walk-backward backfill.
 */
@Component
class BackfillCandidateReader {

    private final JdbcTemplate jdbcTemplate;

    BackfillCandidateReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Symbols not DISMISSED and not already promoted (see the class doc on the live-promotion
     * exclusion) whose {@code discovered_prices} row count *strictly before* their own
     * {@link BackfillTarget#targetBeforeDate} is below {@code targetRows}.
     *
     * <p>{@code targetBeforeDate} is the earliest {@code deal_date} among the symbol's
     * {@code ownership.discovered_deals} rows with no matching {@code ownership.deal_materiality}
     * row yet - real evidence caught live: a symbol discovered the day after its first deal (the
     * common case) would otherwise get "20 total rows" ending exactly *on* that deal's own date,
     * one session short of the 20 {@code MarketLiquidityReader} needs strictly before it, and that
     * gap never self-heals since the daily gate only ever adds rows going forward. Falls back to
     * {@code today} (bound as a parameter, not {@code CURRENT_DATE}, so it stays consistent with
     * the caller's injected {@code Clock}) when a symbol has no unscored deal yet - the original,
     * unconstrained "just keep building history" behavior.
     */
    List<BackfillTarget> findSymbolsNeedingBackfill(int targetRows, LocalDate today) {
        return jdbcTemplate.query(
            """
            SELECT ds.symbol, target.target_before_date
            FROM ownership.discovery_status ds
            JOIN LATERAL (
                SELECT COALESCE(
                    (SELECT MIN(d.deal_date) FROM ownership.discovered_deals d
                     WHERE d.symbol = ds.symbol
                     AND NOT EXISTS (SELECT 1 FROM ownership.deal_materiality m WHERE m.discovered_deal_id = d.id)),
                    ?
                ) AS target_before_date
            ) target ON true
            WHERE ds.status != 'DISMISSED'
              AND NOT EXISTS (SELECT 1 FROM reference.instruments i WHERE i.symbol = ds.symbol)
              AND (
                  SELECT COUNT(*) FROM market.discovered_prices dp
                  WHERE dp.symbol = ds.symbol AND dp.trade_date < target.target_before_date
              ) < ?
            """,
            (rs, rowNum) -> new BackfillTarget(rs.getString("symbol"), rs.getDate("target_before_date").toLocalDate()),
            Date.valueOf(today), targetRows
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
