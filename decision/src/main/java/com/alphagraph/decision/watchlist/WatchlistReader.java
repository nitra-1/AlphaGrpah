package com.alphagraph.decision.watchlist;

import com.alphagraph.decision.api.WatchlistItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class WatchlistReader {

    private final JdbcTemplate jdbcTemplate;

    WatchlistReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Every watched instrument, most recently added first. */
    List<WatchlistItem> findAll() {
        return jdbcTemplate.query(
            "SELECT id, instrument_id, symbol, added_at FROM decision.watchlist_items ORDER BY added_at DESC",
            (rs, rowNum) -> new WatchlistItem(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("instrument_id"),
                rs.getString("symbol"), rs.getTimestamp("added_at").toInstant()
            )
        );
    }

    Optional<WatchlistItem> findByInstrument(UUID instrumentId) {
        List<WatchlistItem> rows = jdbcTemplate.query(
            "SELECT id, instrument_id, symbol, added_at FROM decision.watchlist_items WHERE instrument_id = ?",
            (rs, rowNum) -> new WatchlistItem(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("instrument_id"),
                rs.getString("symbol"), rs.getTimestamp("added_at").toInstant()
            ),
            instrumentId
        );
        return rows.stream().findFirst();
    }
}
