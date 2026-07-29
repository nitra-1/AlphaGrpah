package com.alphagraph.sector.mapping;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reads sector definitions and their constituent instruments from reference.sectors/instruments. */
@Component
public class SectorMappingReader {

    private final JdbcTemplate jdbcTemplate;

    public SectorMappingReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Every sector that has at least one instrument assigned to it. */
    public List<Map<String, Object>> sectorsWithConstituents() {
        return jdbcTemplate.queryForList(
            """
            SELECT DISTINCT s.id AS sector_id, s.name AS sector_name
            FROM reference.sectors s
            JOIN reference.instruments i ON i.sector_id = s.id
            ORDER BY s.name
            """
        );
    }

    public List<UUID> constituentInstrumentIds(UUID sectorId) {
        return jdbcTemplate.query(
            "SELECT id FROM reference.instruments WHERE sector_id = ?",
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            sectorId
        );
    }

    /** Every instrument tracked at all, regardless of sector - the market-wide baseline. */
    public List<UUID> allTrackedInstrumentIds() {
        return jdbcTemplate.query(
            "SELECT id FROM reference.instruments",
            (rs, rowNum) -> (UUID) rs.getObject("id")
        );
    }
}
