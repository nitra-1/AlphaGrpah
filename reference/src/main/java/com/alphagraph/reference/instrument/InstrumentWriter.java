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
