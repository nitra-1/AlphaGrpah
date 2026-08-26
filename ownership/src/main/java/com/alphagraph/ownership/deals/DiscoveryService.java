package com.alphagraph.ownership.deals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The admin's one decision on a Discovery candidate that isn't handled by promotion. */
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
}
