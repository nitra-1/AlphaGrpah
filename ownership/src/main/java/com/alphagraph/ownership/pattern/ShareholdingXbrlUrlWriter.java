package com.alphagraph.ownership.pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Captures a shareholding quarter's real XBRL filing URL as a side effect of normalizing that
 * quarter - the same "capture something extra at normalize-time without changing the return type"
 * pattern {@code ownership.deals.DiscoveredDealWriter} established for bulk deals. Deliberately
 * best-effort: a failure here must never suppress or change {@link ShareholdingNormalizer}'s real
 * return value, so any exception is caught and logged, never propagated.
 *
 * <p>Written here rather than threaded through {@code ownership.api.ShareholdingPattern} so the
 * XBRL enrichment pass never needs to widen that record - it stays exactly the shape
 * {@code ownership.engine}/{@code intelligence}/{@code api.admin} already read.
 */
@Component
class ShareholdingXbrlUrlWriter {

    private static final Logger log = LoggerFactory.getLogger(ShareholdingXbrlUrlWriter.class);

    private final JdbcTemplate jdbcTemplate;

    ShareholdingXbrlUrlWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void capture(UUID instrumentId, LocalDate periodEnd, String xbrlUrl) {
        if (xbrlUrl == null || xbrlUrl.isBlank()) {
            return;
        }
        try {
            jdbcTemplate.update(
                """
                INSERT INTO ownership.shareholding_xbrl_urls (instrument_id, period_end, xbrl_url)
                VALUES (?, ?, ?)
                ON CONFLICT (instrument_id, period_end) DO UPDATE SET xbrl_url = EXCLUDED.xbrl_url
                """,
                instrumentId, periodEnd, xbrlUrl
            );
        } catch (Exception e) {
            log.warn("Failed to capture XBRL URL for instrument {} period {}: {}", instrumentId, periodEnd, e.getMessage());
        }
    }
}
