package com.alphagraph.reference.instrument;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * reference.instruments' first-ever runtime write path - every prior instrument (the original 8
 * plus batch 1's 12) was added via a Flyway migration, never at application runtime. Symbol/name/
 * ISIN are expected to already be verified against reference.security_master by the caller
 * (api.admin.InstrumentAdditionService) - this class only enforces "not already tracked" and does
 * the actual insert, it never re-verifies against NSE itself.
 */
@Component
public class InstrumentWriter {

    private final JdbcTemplate jdbcTemplate;

    public InstrumentWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Empty if the symbol is already tracked (NSE exchange, this project tracks NSE only). */
    public Optional<UUID> create(String symbol, String companyName, String isin, UUID sectorId) {
        if (isAlreadyTracked(symbol)) {
            return Optional.empty();
        }

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO reference.instruments (id, symbol, exchange_id, name, isin, instrument_type, sector_id)
            SELECT ?, ?, e.id, ?, ?, 'EQUITY', ?
            FROM reference.exchanges e WHERE e.code = 'NSE'
            """,
            id, symbol, companyName, isin, sectorId
        );
        return Optional.of(id);
    }

    /**
     * Reassigns an already-tracked instrument to a different sector (or clears it, if
     * {@code sectorId} is null - the column has always been nullable). Exists specifically so a
     * sector blocked from deletion by {@code SectorService.delete} (instruments still assigned)
     * can actually be resolved from the admin UI, not just diagnosed.
     *
     * @return false if no instrument exists with this id
     * @throws IllegalArgumentException if sectorId is given but no sector exists with that id
     */
    public boolean updateSector(UUID instrumentId, UUID sectorId) {
        Long instrumentCount = jdbcTemplate.queryForObject("SELECT count(*) FROM reference.instruments WHERE id = ?", Long.class, instrumentId);
        if (instrumentCount == null || instrumentCount == 0) {
            return false;
        }
        if (sectorId != null) {
            Long sectorCount = jdbcTemplate.queryForObject("SELECT count(*) FROM reference.sectors WHERE id = ?", Long.class, sectorId);
            if (sectorCount == null || sectorCount == 0) {
                throw new IllegalArgumentException("No sector with id " + sectorId);
            }
        }
        jdbcTemplate.update("UPDATE reference.instruments SET sector_id = ? WHERE id = ?", sectorId, instrumentId);
        return true;
    }

    private boolean isAlreadyTracked(String symbol) {
        try {
            jdbcTemplate.queryForObject(
                "SELECT id FROM reference.instruments WHERE symbol = ?", UUID.class, symbol
            );
            return true;
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }
}
