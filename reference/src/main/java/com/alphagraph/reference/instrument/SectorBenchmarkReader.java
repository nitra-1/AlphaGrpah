package com.alphagraph.reference.instrument;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads {@code reference.sector_benchmarks} - the verified sector-to-tradable-index mapping used
 * by {@code learning.outcomes.BenchmarkReturnCalculator} for sector-relative forward returns. Public:
 * a foundational reference-data reader every module already depends on directly (the same pattern
 * as {@code InstrumentReader}), not a domain-to-domain cross-module call subject to Rule 4.
 */
@Component
public class SectorBenchmarkReader {

    private final JdbcTemplate jdbcTemplate;

    public SectorBenchmarkReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The verified benchmark index instrument for the sector a tracked instrument belongs to - empty if that instrument has no sector, or its sector has no verified mapping (never guessed). */
    public Optional<UUID> findBenchmarkInstrumentIdForInstrument(UUID instrumentId) {
        List<UUID> rows = jdbcTemplate.query(
            """
            SELECT sb.benchmark_instrument_id
            FROM reference.instruments i
            JOIN reference.sector_benchmarks sb ON sb.sector_id = i.sector_id
            WHERE i.id = ?
            """,
            (rs, rowNum) -> (UUID) rs.getObject("benchmark_instrument_id"),
            instrumentId
        );
        return rows.stream().findFirst();
    }
}
