package com.alphagraph.decision.watchlist;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Raw persistence for decision.watchlist_items - no business logic, that's {@link WatchlistService}'s job. */
@Component
class WatchlistStore {

    private final JdbcTemplate jdbcTemplate;

    WatchlistStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** No-op if the instrument is already on the list - idempotent, mirroring corporate.relationships.RelationshipWriter's ON CONFLICT DO NOTHING pattern. */
    void insert(UUID instrumentId, String symbol) {
        jdbcTemplate.update(
            "INSERT INTO decision.watchlist_items (id, instrument_id, symbol) VALUES (?, ?, ?) ON CONFLICT (instrument_id) DO NOTHING",
            UUID.randomUUID(), instrumentId, symbol
        );
    }

    /** @return true if a row was actually removed. */
    boolean delete(UUID instrumentId) {
        int rowsAffected = jdbcTemplate.update("DELETE FROM decision.watchlist_items WHERE instrument_id = ?", instrumentId);
        return rowsAffected > 0;
    }
}
