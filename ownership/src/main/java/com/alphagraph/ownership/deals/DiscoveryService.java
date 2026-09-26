package com.alphagraph.ownership.deals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The admin's decisions on a Discovery candidate. */
@Component
public class DiscoveryService {

    private final JdbcTemplate jdbcTemplate;

    public DiscoveryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Terminal - a dismissed symbol stops appearing in {@link DiscoveryReader#findPendingReview}
     * even if new deal activity for it arrives later. Idempotency guard matches
     * {@code corporate.newsfeed.NewsReviewService}'s status-flip shape: the WHERE clause requires
     * the row not already be dismissed, so a double-click is a no-op returning {@code false}.
     */
    public boolean discard(String symbol) {
        int updated = jdbcTemplate.update(
            "UPDATE ownership.discovery_status SET status = 'DISMISSED' WHERE symbol = ? AND status != 'DISMISSED'",
            symbol
        );
        return updated > 0;
    }

    /**
     * Called by {@code api.admin.InstrumentAdditionService} on every successful "Add Instrument",
     * not just ones that started from the Discovery page - a symbol only has a {@code
     * discovery_status} row at all if it once had real bulk/block deal activity, so this is a
     * harmless no-op (returns {@code false}) for an ordinary manually-added symbol. Same
     * idempotency-guard shape as {@link #discard}. {@link DiscoveryReader#findPendingReview}
     * already excludes any symbol present in {@code reference.instruments} regardless of this
     * flag - this exists so the admin's own status history is accurate, not to gate visibility.
     */
    public boolean markPromoted(String symbol) {
        int updated = jdbcTemplate.update(
            "UPDATE ownership.discovery_status SET status = 'PROMOTED' WHERE symbol = ? AND status != 'PROMOTED'",
            symbol
        );
        return updated > 0;
    }
}
