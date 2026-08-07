package com.alphagraph.decision.analyst;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Looks up an already-generated explanation for today, so {@link AiAnalystService} can skip the Claude call entirely on a cache hit. */
@Component
class AnalystExplanationReader {

    private final JdbcTemplate jdbcTemplate;

    AnalystExplanationReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<String> find(UUID instrumentId, String explanationType, LocalDate businessDate) {
        List<String> rows = jdbcTemplate.query(
            "SELECT explanation FROM decision.analyst_explanations WHERE instrument_id = ? AND explanation_type = ? AND business_date = ?",
            (rs, rowNum) -> rs.getString("explanation"),
            instrumentId, explanationType, Date.valueOf(businessDate)
        );
        return rows.stream().findFirst();
    }
}
