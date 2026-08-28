package com.alphagraph.market.pricing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Answers "is this symbol a genuine bulk/block deal Discovery candidate?" - the gate that keeps
 * {@link DiscoveredPriceWriter} from turning into "collect daily prices for the entire untracked
 * NSE universe." Reads {@code ownership.discovery_status} directly by raw SQL, cross-schema by
 * value only, the same established pattern {@code ownership.pattern.OwnershipInstrumentLookup}
 * already uses for {@code reference.instruments} - {@code market} gains no Java dependency on the
 * {@code ownership} module.
 */
@Component
class DiscoveryCandidateLookup {

    private final JdbcTemplate jdbcTemplate;

    DiscoveryCandidateLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean isCandidate(String symbol) {
        Boolean exists = jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM ownership.discovery_status WHERE symbol = ?)",
            Boolean.class, symbol
        );
        return Boolean.TRUE.equals(exists);
    }
}
