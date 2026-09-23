package com.alphagraph.discovery.convergence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads {@code corporate.transformation_sequences} - Capital Allocation lives in the {@code corporate} module/schema. */
@Component
class CapitalAllocationSequenceReader extends AbstractSequenceReader {
    CapitalAllocationSequenceReader(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, "corporate", "history_days");
    }
}
