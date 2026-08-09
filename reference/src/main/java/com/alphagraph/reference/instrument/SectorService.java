package com.alphagraph.reference.instrument;

import com.alphagraph.reference.api.SectorSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * reference.sectors has no unique constraint on name (confirmed the hard way at Module 1.8 -
 * V4's dummy tree already had a "Capital Goods" row, and a naive second insert made
 * "WHERE name = 'Capital Goods'" ambiguous) - {@code findOrCreateByName} always looks up first
 * and only inserts on a genuine miss, picking the oldest match if duplicates somehow exist rather
 * than crashing on ambiguity.
 */
@Component
public class SectorService {

    private final JdbcTemplate jdbcTemplate;

    public SectorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SectorSummary> listAll() {
        return jdbcTemplate.query(
            "SELECT id, name FROM reference.sectors ORDER BY name",
            (rs, rowNum) -> new SectorSummary((UUID) rs.getObject("id"), rs.getString("name"))
        );
    }

    public UUID findOrCreateByName(String name) {
        List<UUID> existing = jdbcTemplate.query(
            "SELECT id FROM reference.sectors WHERE name = ? ORDER BY created_at LIMIT 1",
            (rs, rowNum) -> (UUID) rs.getObject("id"), name
        );
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO reference.sectors (id, name) VALUES (?, ?)", id, name);
        return id;
    }
}
