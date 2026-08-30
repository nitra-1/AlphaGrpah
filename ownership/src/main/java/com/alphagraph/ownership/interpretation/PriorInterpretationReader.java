package com.alphagraph.ownership.interpretation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class PriorInterpretationReader {

    private final JdbcTemplate jdbcTemplate;

    PriorInterpretationReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<PriorInterpretation> findLatest(String symbol) {
        List<PriorInterpretation> rows = jdbcTemplate.query(
            """
            SELECT institutional_state, event_anchor_date
            FROM ownership.institutional_interpretations
            WHERE symbol = ?
            ORDER BY as_of_date DESC
            LIMIT 1
            """,
            (rs, rowNum) -> new PriorInterpretation(
                InstitutionalState.valueOf(rs.getString("institutional_state")),
                rs.getDate("event_anchor_date") == null ? null : rs.getDate("event_anchor_date").toLocalDate()
            ),
            symbol
        );
        return rows.stream().findFirst();
    }
}
