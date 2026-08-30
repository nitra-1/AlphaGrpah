package com.alphagraph.ownership.deals;

import com.alphagraph.ownership.api.DiscoveryCandidate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Aggregates {@code ownership.discovered_deals} at read time (deal count, distinct buyers/sellers,
 * total quantity, first/latest deal date) for the admin Discovery review page - not a separately
 * maintained aggregate table, matching this codebase's existing "compute views at read time"
 * convention (e.g. {@code api.admin.CronMonitoringRepository}'s {@code LEFT JOIN LATERAL}).
 *
 * <p>Excludes any symbol already in {@code reference.instruments} (promoted - detected live, not
 * via a status flag written by the promote action, so this can never drift out of sync with the
 * real tracked universe) or already {@code DISMISSED} in {@code ownership.discovery_status}.
 * Cross-schema read by value only, same established pattern as
 * {@code ownership.pattern.OwnershipInstrumentLookup}.
 *
 * <p>Every aggregate excludes {@code duplicate_of_deal_id IS NOT NULL} rows - real evidence caught
 * live: NSE's bulk and block deal feeds can both independently report the exact same trade, and
 * without this exclusion a genuinely single real trade gets summed twice (see V11's migration
 * comment and {@code DiscoveredDealWriter}). {@code distinct_buyers}/{@code distinct_sellers} are
 * each filtered to their own side - a real, pre-Sprint-3 bug caught during the same investigation
 * had this counting *all* distinct participants regardless of side under the name "distinct_buyers".
 *
 * <p>Ordered by most-recently-active symbol first, but real data ties heavily on that alone - the
 * daily cron typically leaves dozens of symbols sharing the same {@code latest_deal_date} (today),
 * so {@code deal_count} is a real secondary sort key (more persistent activity first among
 * same-day symbols), with {@code symbol} as a final deterministic tie-break so the order never
 * looks arbitrary between two otherwise-identical candidates.
 */
@Component
public class DiscoveryReader {

    private final JdbcTemplate jdbcTemplate;

    public DiscoveryReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DiscoveryCandidate> findPendingReview() {
        return jdbcTemplate.query(
            """
            SELECT d.symbol,
                   MAX(d.security_name) AS security_name,
                   COUNT(*) AS deal_count,
                   COUNT(DISTINCT d.client_name) FILTER (WHERE d.buy_sell = 'BUY') AS distinct_buyers,
                   COUNT(DISTINCT d.client_name) FILTER (WHERE d.buy_sell = 'SELL') AS distinct_sellers,
                   SUM(d.quantity) AS total_quantity,
                   MIN(d.deal_date) AS first_deal_date,
                   MAX(d.deal_date) AS latest_deal_date,
                   best.materiality_score AS max_materiality_score,
                   best.materiality_level AS max_materiality_level,
                   widest.largest_ratio AS largest_deal_to_adtv_ratio,
                   interp.event_structure, interp.institutional_state, interp.discovery_confirmation_state,
                   interp.confidence AS interpretation_confidence, interp.churn_state,
                   interp.confirmation_sessions_elapsed, interp.confirmation_frozen, interp.interpretation_readiness
            FROM ownership.discovered_deals d
            LEFT JOIN LATERAL (
                SELECT m.materiality_score, m.materiality_level
                FROM ownership.deal_materiality m
                WHERE m.symbol = d.symbol
                ORDER BY m.materiality_score DESC
                LIMIT 1
            ) best ON true
            LEFT JOIN LATERAL (
                SELECT MAX(m2.deal_to_adtv_ratio) AS largest_ratio
                FROM ownership.deal_materiality m2
                WHERE m2.symbol = d.symbol
            ) widest ON true
            LEFT JOIN LATERAL (
                SELECT ii.event_structure, ii.institutional_state, ii.discovery_confirmation_state, ii.confidence,
                       ii.churn_state, ii.confirmation_sessions_elapsed, ii.confirmation_frozen, ii.interpretation_readiness
                FROM ownership.institutional_interpretations ii
                WHERE ii.symbol = d.symbol
                ORDER BY ii.as_of_date DESC
                LIMIT 1
            ) interp ON true
            WHERE d.duplicate_of_deal_id IS NULL
              AND NOT EXISTS (SELECT 1 FROM reference.instruments i WHERE i.symbol = d.symbol)
              AND NOT EXISTS (
                  SELECT 1 FROM ownership.discovery_status s WHERE s.symbol = d.symbol AND s.status = 'DISMISSED'
              )
            GROUP BY d.symbol, best.materiality_score, best.materiality_level, widest.largest_ratio,
                     interp.event_structure, interp.institutional_state, interp.discovery_confirmation_state,
                     interp.confidence, interp.churn_state, interp.confirmation_sessions_elapsed,
                     interp.confirmation_frozen, interp.interpretation_readiness
            ORDER BY latest_deal_date DESC, max_materiality_score DESC NULLS LAST, deal_count DESC, d.symbol
            """,
            (rs, rowNum) -> new DiscoveryCandidate(
                rs.getString("symbol"), rs.getString("security_name"), rs.getInt("deal_count"),
                rs.getInt("distinct_buyers"), rs.getInt("distinct_sellers"), rs.getLong("total_quantity"),
                toLocalDate(rs.getDate("first_deal_date")), toLocalDate(rs.getDate("latest_deal_date")),
                toDouble(rs.getBigDecimal("max_materiality_score")), rs.getString("max_materiality_level"),
                toDouble(rs.getBigDecimal("largest_deal_to_adtv_ratio")),
                rs.getString("event_structure"), rs.getString("institutional_state"),
                rs.getString("discovery_confirmation_state"), toDouble(rs.getBigDecimal("interpretation_confidence")),
                rs.getString("churn_state"), rs.getInt("confirmation_sessions_elapsed"), rs.getBoolean("confirmation_frozen"),
                rs.getString("interpretation_readiness")
            )
        );
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
