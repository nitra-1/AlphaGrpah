package com.alphagraph.financial.engine;

import com.alphagraph.financial.api.BusinessGrowth;
import com.alphagraph.financial.api.CapitalEfficiency;
import com.alphagraph.financial.api.FinancialQuality;
import com.alphagraph.financial.api.FundamentalScore;
import com.alphagraph.financial.api.Profitability;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads financial.fundamental_scores directly - it's this module's own table. Added in Module
 * 1.9 (Risk Engine): Module 1.6 only needed a {@link FundamentalScoreWriter}, since nothing had
 * to read a fundamental score back until the Risk Engine needed it as an input.
 */
@Component
public class FundamentalScoreReader {

    private final JdbcTemplate jdbcTemplate;

    public FundamentalScoreReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<FundamentalScore> findLatest(UUID instrumentId) {
        List<FundamentalScore> rows = jdbcTemplate.query(
            """
            SELECT fs.instrument_id, i.symbol, fs.as_of_date, fs.business_growth, fs.profitability,
                   fs.capital_efficiency, fs.financial_quality, fs.financial_score, fs.confidence,
                   fs.revenue_growth_percentage, fs.pat_growth_percentage, fs.eps_growth_percentage,
                   fs.roe_percentage, fs.roce_percentage, fs.operating_margin_percentage,
                   fs.net_margin_percentage, fs.asset_turnover, fs.working_capital, fs.cash_conversion_ratio,
                   fs.debt_to_equity, fs.interest_coverage, fs.rule_set_version, fs.computed_at
            FROM financial.fundamental_scores fs
            JOIN reference.instruments i ON i.id = fs.instrument_id
            WHERE fs.instrument_id = ?
            ORDER BY fs.as_of_date DESC
            LIMIT 1
            """,
            this::mapRow,
            instrumentId
        );
        return rows.stream().findFirst();
    }

    private FundamentalScore mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new FundamentalScore(
            (UUID) rs.getObject("instrument_id"), rs.getString("symbol"), rs.getDate("as_of_date").toLocalDate(),
            BusinessGrowth.valueOf(rs.getString("business_growth")), Profitability.valueOf(rs.getString("profitability")),
            CapitalEfficiency.valueOf(rs.getString("capital_efficiency")), FinancialQuality.valueOf(rs.getString("financial_quality")),
            rs.getDouble("financial_score"), rs.getDouble("confidence"),
            toDouble(rs.getBigDecimal("revenue_growth_percentage")), toDouble(rs.getBigDecimal("pat_growth_percentage")),
            toDouble(rs.getBigDecimal("eps_growth_percentage")), toDouble(rs.getBigDecimal("roe_percentage")),
            toDouble(rs.getBigDecimal("roce_percentage")), toDouble(rs.getBigDecimal("operating_margin_percentage")),
            toDouble(rs.getBigDecimal("net_margin_percentage")), toDouble(rs.getBigDecimal("asset_turnover")),
            toDouble(rs.getBigDecimal("working_capital")), toDouble(rs.getBigDecimal("cash_conversion_ratio")),
            toDouble(rs.getBigDecimal("debt_to_equity")), toDouble(rs.getBigDecimal("interest_coverage")),
            rs.getInt("rule_set_version"), rs.getTimestamp("computed_at").toInstant()
        );
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
