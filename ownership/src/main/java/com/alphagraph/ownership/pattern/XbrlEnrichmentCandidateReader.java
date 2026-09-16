package com.alphagraph.ownership.pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Finds shareholding quarters never yet attempted for XBRL enrichment ({@code xbrl_enriched_at IS
 * NULL}) that also have a captured real filing URL to fetch - a quarter with no
 * {@code shareholding_xbrl_urls} row yet (the live collector hasn't run for it, or capture failed)
 * simply isn't a candidate until one exists.
 *
 * <p>Deliberately keyed off "attempted," not "all four sub-category columns are non-null" (V13's
 * original design) - a real filing can genuinely have zero holders in one of the four categories
 * (e.g. no foreign portfolio investors at all), and that column staying null forever is a correct,
 * honest outcome, not a sign the period needs re-fetching every single day indefinitely. See V14's
 * migration comment.
 */
@Component
class XbrlEnrichmentCandidateReader {

    private final JdbcTemplate jdbcTemplate;

    XbrlEnrichmentCandidateReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Ordered by instrument then period, capped at {@code maxPeriods} - batched by the caller so one run never fetches unbounded XBRL documents. */
    List<PendingXbrlPeriod> findPending(int maxPeriods) {
        return jdbcTemplate.query(
            """
            SELECT sp.instrument_id, i.symbol, sp.period_end, u.xbrl_url, sp.promoter_percentage
            FROM ownership.shareholding_pattern sp
            JOIN reference.instruments i ON i.id = sp.instrument_id
            JOIN ownership.shareholding_xbrl_urls u ON u.instrument_id = sp.instrument_id AND u.period_end = sp.period_end
            WHERE sp.xbrl_enriched_at IS NULL
            ORDER BY sp.instrument_id, sp.period_end
            LIMIT ?
            """,
            (rs, rowNum) -> new PendingXbrlPeriod(
                (UUID) rs.getObject("instrument_id"), rs.getString("symbol"),
                rs.getDate("period_end").toLocalDate(), rs.getString("xbrl_url"), rs.getBigDecimal("promoter_percentage")
            ),
            maxPeriods
        );
    }
}
