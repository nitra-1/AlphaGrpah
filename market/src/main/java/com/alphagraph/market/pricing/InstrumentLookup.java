package com.alphagraph.market.pricing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Resolves a symbol against reference.instruments — the "symbol resolution against reference" a Normalizer does, per docs/002_Engine_Architecture.md §2. */
@Component
public class InstrumentLookup {

    private final JdbcTemplate jdbcTemplate;

    public InstrumentLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UUID> findIdBySymbol(String symbol) {
        return jdbcTemplate.query(
            "SELECT id FROM reference.instruments WHERE symbol = ?",
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            symbol
        ).stream().findFirst();
    }
}
