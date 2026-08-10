package com.alphagraph.decision.watchlist;

import com.alphagraph.decision.api.WatchlistItem;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the single global watchlist (Module 3.2) - add/remove/list. Returns {@link Optional#empty()}
 * from {@link #add} when the instrument doesn't exist in reference.instruments at all, rather than
 * throwing an api-module exception type this module cannot depend on (see the "no module depends
 * on the api module" rule, docs/001_System_Architecture.md §4) - the caller (api.watchlist)
 * translates that into a 404, the same {@code Optional -> orElseThrow} shape
 * api.rule.RuleController.activate() already uses for the same reason.
 */
@Component
public class WatchlistService {

    private final JdbcTemplate jdbcTemplate;
    private final WatchlistStore store;
    private final WatchlistReader reader;

    public WatchlistService(JdbcTemplate jdbcTemplate, WatchlistStore store, WatchlistReader reader) {
        this.jdbcTemplate = jdbcTemplate;
        this.store = store;
        this.reader = reader;
    }

    public Optional<WatchlistItem> add(UUID userId, UUID instrumentId) {
        String symbol;
        try {
            symbol = jdbcTemplate.queryForObject("SELECT symbol FROM reference.instruments WHERE id = ?", String.class, instrumentId);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
        store.insert(userId, instrumentId, symbol);
        return reader.findByInstrument(userId, instrumentId);
    }

    public boolean remove(UUID userId, UUID instrumentId) {
        return store.delete(userId, instrumentId);
    }

    public List<WatchlistItem> list(UUID userId) {
        return reader.findAll(userId);
    }
}
