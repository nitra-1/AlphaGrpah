package com.alphagraph.discovery.convergence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * New reader - no class in this codebase reads {@code risk.contradiction_states} back at all
 * today ({@code risk.contradiction.RiskContradictionWriter} is write-only). Mirrors {@code
 * intelligence.riskcontradiction.FinancialGrowthReader.findStateAsOf}'s exact {@code as_of_date <=
 * ? ORDER BY as_of_date DESC LIMIT 1} shape. Stays freshness-agnostic on purpose - {@link
 * DiscoveryConvergenceEngine} applies the {@code stage4-risk-contradiction-max-age-days} gate
 * itself against the returned row's own {@code asOfDate}, so it can still record a {@code
 * STALE_CONTRADICTION_PRESENT} reason even when the penalty itself is zeroed out.
 */
@Component
class RiskContradictionOverlayReader {

    private final JdbcTemplate jdbcTemplate;

    RiskContradictionOverlayReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<ContradictionOverlayRow> findStateAsOf(UUID instrumentId, LocalDate asOfDate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, primary_state, confidence, as_of_date FROM risk.contradiction_states " +
            "WHERE instrument_id = ? AND as_of_date <= ? ORDER BY as_of_date DESC LIMIT 1",
            instrumentId, Date.valueOf(asOfDate)
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        UUID stateId = (UUID) row.get("id");
        List<String> reasonCodes = jdbcTemplate.query(
            "SELECT reason_code FROM risk.contradiction_state_reasons WHERE state_id = ?",
            (rs, rowNum) -> rs.getString("reason_code"),
            stateId
        );
        return Optional.of(new ContradictionOverlayRow(
            (String) row.get("primary_state"),
            ((Number) row.get("confidence")).doubleValue(),
            ((Date) row.get("as_of_date")).toLocalDate(),
            reasonCodes
        ));
    }
}
