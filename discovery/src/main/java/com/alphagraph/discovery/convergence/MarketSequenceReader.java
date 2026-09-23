package com.alphagraph.discovery.convergence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class MarketSequenceReader extends AbstractSequenceReader {
    MarketSequenceReader(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, "market", "history_sessions");
    }
}
