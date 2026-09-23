package com.alphagraph.discovery.convergence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class SectorSequenceReader extends AbstractSequenceReader {
    SectorSequenceReader(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, "sector", "history_sessions");
    }
}
