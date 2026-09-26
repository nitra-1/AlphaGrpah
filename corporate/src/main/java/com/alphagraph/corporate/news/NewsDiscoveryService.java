package com.alphagraph.corporate.news;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The admin's decisions on a {@code corporate.news_discovery_candidates} row - a real, explicit
 * lifecycle ({@code NEW -> UNDER_OBSERVATION -> PROMOTED | DISMISSED}), distinct from {@code
 * ownership.deals.DiscoveryService}'s own status (a different root cause - news exposure, not
 * bulk/block deals). Every mutation here uses the same idempotency-guard shape as that service:
 * the WHERE clause requires the row not already be in the target state, so a double-click is a
 * harmless no-op returning {@code false}.
 */
@Component
public class NewsDiscoveryService {

    private final JdbcTemplate jdbcTemplate;

    public NewsDiscoveryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The explicit "an analyst has looked at this and is watching it" marker - distinct from both
     * ignoring the candidate and from promoting it, so the UI can visually separate "just
     * detected" from "someone's actually watching this." Only valid from {@code NEW}; calling it
     * on a candidate already {@code UNDER_OBSERVATION}/{@code PROMOTED}/{@code DISMISSED} is a
     * no-op, not an error - the UI simply won't offer the action once the badge has moved on.
     */
    public boolean observe(String symbol) {
        int updated = jdbcTemplate.update(
            "UPDATE corporate.news_discovery_candidates SET status = 'UNDER_OBSERVATION' WHERE symbol = ? AND status = 'NEW'",
            symbol
        );
        return updated > 0;
    }

    /** Terminal - a dismissed candidate stops being actionable, but stays visible in the Untracked tab for audit. */
    public boolean dismiss(String symbol) {
        int updated = jdbcTemplate.update(
            "UPDATE corporate.news_discovery_candidates SET status = 'DISMISSED' WHERE symbol = ? AND status != 'DISMISSED'",
            symbol
        );
        return updated > 0;
    }

    /**
     * Called by {@code api.admin.InstrumentAdditionService} on every successful "Add Instrument",
     * alongside the pre-existing {@code ownership.deals.DiscoveryService.markPromoted} call - a
     * symbol only has a {@code news_discovery_candidates} row at all if it was once identified as
     * exposed to a real economic event, so this is a harmless no-op for an ordinary manually-added
     * symbol never surfaced by this module. Promotion itself is never triggered from here - it
     * only ever happens through that existing, unmodified Add Instrument flow.
     */
    public boolean markPromoted(String symbol) {
        int updated = jdbcTemplate.update(
            "UPDATE corporate.news_discovery_candidates SET status = 'PROMOTED' WHERE symbol = ? AND status != 'PROMOTED'",
            symbol
        );
        return updated > 0;
    }
}
