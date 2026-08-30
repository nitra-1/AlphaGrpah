package com.alphagraph.ownership.deals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;

/**
 * Direction-aware repetition/breadth, both-side evidence, and reported buy/sell totals against
 * {@code ownership.discovered_deals}, calendar-day windowed (deals are sparse/event-driven, no
 * "one row per trading day" structure to count in trading sessions - see the 20CalendarDays
 * naming convention this and every sibling field in {@link BulkDealContext} follows) to the 20
 * calendar days ending on and including the deal being scored's own date.
 *
 * <p>Repetition/breadth resolve by the deal's own {@code buy_sell} side, not unconditionally
 * "buyer" - scoring only "same buyer"/"distinct buyers" would silently suppress a highly material
 * SELL-side deal's score, since a pure-SELL deal has no buyer activity to measure. The caller
 * passes the deal's own {@code direction}, and this reader filters "same side"/"distinct same
 * side" by it - so a repeated seller scores exactly as high as a repeated buyer.
 * {@code distinctBuyers}/{@code distinctSellers} (both sides, always computed regardless of
 * direction) are separate unbiased evidence fields, never used as {@link DealMaterialityEngine}'s
 * scoring input.
 *
 * <p>Excludes {@code duplicate_of_deal_id IS NOT NULL} rows - the same real cross-feed BULK/BLOCK
 * overlap {@code ownership.deals.DiscoveryReader} excludes (see V11's migration comment); without
 * this a single real trade reported in both feeds would be summed into reported buy/sell value
 * twice.
 */
@Component
class BulkDealContextReader {

    private static final int WINDOW_CALENDAR_DAYS = 20;

    private final JdbcTemplate jdbcTemplate;

    BulkDealContextReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    BulkDealContext findContext(String symbol, LocalDate dealDate, String direction, String clientNameNormalized) {
        LocalDate windowStart = dealDate.minusDays(WINDOW_CALENDAR_DAYS - 1L);

        return jdbcTemplate.queryForObject(
            """
            SELECT
                COUNT(*) FILTER (WHERE buy_sell = ? AND client_name_normalized = ?) AS same_side_count,
                COUNT(DISTINCT client_name_normalized) FILTER (WHERE buy_sell = ?) AS distinct_same_side,
                COUNT(DISTINCT client_name_normalized) FILTER (WHERE buy_sell = 'BUY') AS distinct_buyers,
                COUNT(DISTINCT client_name_normalized) FILTER (WHERE buy_sell = 'SELL') AS distinct_sellers,
                COALESCE(SUM(deal_value) FILTER (WHERE buy_sell = 'BUY'), 0) AS reported_buy_value,
                COALESCE(SUM(deal_value) FILTER (WHERE buy_sell = 'SELL'), 0) AS reported_sell_value
            FROM ownership.discovered_deals
            WHERE symbol = ? AND deal_date BETWEEN ? AND ? AND duplicate_of_deal_id IS NULL
            """,
            (rs, rowNum) -> new BulkDealContext(
                rs.getInt("same_side_count"), rs.getInt("distinct_same_side"),
                rs.getInt("distinct_buyers"), rs.getInt("distinct_sellers"),
                rs.getBigDecimal("reported_buy_value"), rs.getBigDecimal("reported_sell_value")
            ),
            direction, clientNameNormalized, direction, symbol, Date.valueOf(windowStart), Date.valueOf(dealDate)
        );
    }
}
