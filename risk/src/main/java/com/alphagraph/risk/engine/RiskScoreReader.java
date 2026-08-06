package com.alphagraph.risk.engine;

import com.alphagraph.risk.api.RiskLevel;
import com.alphagraph.risk.api.RiskScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads risk.risk_scores directly - it's this module's own table. Added in Module 3.1 (Scoring/
 * Decision Engine): {@link RiskScoreWriter} was write-only until now, the same "nothing had to
 * read this back until X needed it as an input" pattern every other domain's first reader
 * followed (technical.engine.TechnicalScoreReader, financial.engine.FundamentalScoreReader,
 * ownership.engine.InstitutionalScoreReader - all added in Module 1.9 when the Risk Engine itself
 * needed them).
 */
@Component
public class RiskScoreReader {

    private final JdbcTemplate jdbcTemplate;

    public RiskScoreReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<RiskScore> findLatest(UUID instrumentId) {
        List<RiskScore> rows = jdbcTemplate.query(
            """
            SELECT rs.instrument_id, i.symbol, rs.as_of_date, rs.business_risk, rs.technical_risk,
                   rs.ownership_risk, rs.valuation_risk, rs.overall_risk, rs.risk_score, rs.confidence,
                   rs.pe_ratio, rs.pb_ratio, rs.debt_to_equity, rs.rule_set_version, rs.computed_at
            FROM risk.risk_scores rs
            JOIN reference.instruments i ON i.id = rs.instrument_id
            WHERE rs.instrument_id = ?
            ORDER BY rs.as_of_date DESC
            LIMIT 1
            """,
            this::mapRow,
            instrumentId
        );
        return rows.stream().findFirst();
    }

    private RiskScore mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RiskScore(
            (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
            RiskLevel.valueOf(rs.getString("business_risk")), RiskLevel.valueOf(rs.getString("technical_risk")),
            RiskLevel.valueOf(rs.getString("ownership_risk")), RiskLevel.valueOf(rs.getString("valuation_risk")),
            RiskLevel.valueOf(rs.getString("overall_risk")),
            rs.getDouble("risk_score"), rs.getDouble("confidence"),
            toDouble(rs.getBigDecimal("pe_ratio")), toDouble(rs.getBigDecimal("pb_ratio")), toDouble(rs.getBigDecimal("debt_to_equity")),
            rs.getInt("rule_set_version"), rs.getTimestamp("computed_at").toInstant()
        );
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
