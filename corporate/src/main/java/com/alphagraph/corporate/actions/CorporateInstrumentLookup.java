package com.alphagraph.corporate.actions;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a symbol against reference.instruments. Deliberately a separate copy from market's,
 * ownership's, and financial's InstrumentLookup classes - domain modules never depend on each
 * other directly (only via intelligence), per docs/001_System_Architecture.md §4, so this small
 * lookup is duplicated rather than shared. Named distinctly (not just a different package)
 * because Spring assigns bean names from the simple class name by default - two classes named
 * "InstrumentLookup" in different packages collide at component-scan time (confirmed the hard
 * way in Module 1.2).
 */
@Component
public class CorporateInstrumentLookup {

    private final JdbcTemplate jdbcTemplate;

    public CorporateInstrumentLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UUID> findIdBySymbol(String symbol) {
        return jdbcTemplate.query(
            "SELECT id FROM reference.instruments WHERE symbol = ?",
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            symbol
        ).stream().findFirst();
    }

    /** Every tracked instrument's id - the universe {@code corporate.transformation.CapitalAllocationOrchestrator} walks (most will have zero actions of a given type, a real, not-yet-seen case, not an error). */
    public List<UUID> findAllInstrumentIds() {
        return jdbcTemplate.query(
            "SELECT id FROM reference.instruments ORDER BY symbol",
            (rs, rowNum) -> (UUID) rs.getObject("id")
        );
    }
}
