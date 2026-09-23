package com.alphagraph.discovery.convergence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class FinancialSequenceReader extends AbstractSequenceReader {
    FinancialSequenceReader(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, "financial", "history_periods");
    }
}
