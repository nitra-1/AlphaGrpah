package com.alphagraph.reference.instrument;

import com.alphagraph.reference.api.TrackedInstrumentSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Powers the financial/shareholding data-entry form's instrument picker - every currently tracked instrument, independent of whether it has any scores yet. */
@Component
public class InstrumentReader {

    private final JdbcTemplate jdbcTemplate;

    public InstrumentReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TrackedInstrumentSummary> listAll() {
        return jdbcTemplate.query(
            """
            SELECT i.id, i.symbol, i.name, i.sector_id, s.name AS sector_name
            FROM reference.instruments i
            LEFT JOIN reference.sectors s ON s.id = i.sector_id
            ORDER BY i.symbol
            """,
            (rs, rowNum) -> new TrackedInstrumentSummary(
                (UUID) rs.getObject("id"), rs.getString("symbol"), rs.getString("name"),
                (UUID) rs.getObject("sector_id"), rs.getString("sector_name")
            )
        );
    }

    /** Added for {@code learning.outcomes.BenchmarkReturnCalculator}, which resolves a configured market-benchmark symbol (e.g. "NIFTY50") to its instrument id once at outcome-computation time - empty if that symbol isn't tracked yet. */
    public Optional<UUID> findIdBySymbol(String symbol) {
        List<UUID> rows = jdbcTemplate.query(
            "SELECT id FROM reference.instruments WHERE symbol = ?",
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            symbol
        );
        return rows.stream().findFirst();
    }
}
