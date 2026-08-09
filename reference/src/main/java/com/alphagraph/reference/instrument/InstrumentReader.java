package com.alphagraph.reference.instrument;

import com.alphagraph.reference.api.TrackedInstrumentSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
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
            "SELECT id, symbol, name FROM reference.instruments ORDER BY symbol",
            (rs, rowNum) -> new TrackedInstrumentSummary((UUID) rs.getObject("id"), rs.getString("symbol"), rs.getString("name"))
        );
    }
}
