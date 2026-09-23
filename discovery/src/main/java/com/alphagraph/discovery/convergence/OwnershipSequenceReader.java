package com.alphagraph.discovery.convergence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class OwnershipSequenceReader extends AbstractSequenceReader {
    OwnershipSequenceReader(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, "ownership", "history_periods");
    }
}
