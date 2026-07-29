package com.alphagraph.financial.engine;

import com.alphagraph.financial.api.FinancialResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Reads financial.financial_results directly - no published read interface needed the way
 * market.api.DailyPriceReader was for Module 1.5, since {@link FundamentalAnalysisOrchestrator}
 * lives in this same module and financial already owns this table.
 */
@Component
public class FinancialResultReader {

    private final JdbcTemplate jdbcTemplate;

    public FinancialResultReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UUID> instrumentIdsWithResults() {
        return jdbcTemplate.query(
            "SELECT DISTINCT instrument_id FROM financial.financial_results",
            (rs, rowNum) -> (UUID) rs.getObject("instrument_id")
        );
    }

    public List<FinancialResult> findPeriods(UUID instrumentId) {
        return jdbcTemplate.query(
            """
            SELECT fr.instrument_id, i.symbol, fr.period_end, fr.period_type, fr.sales, fr.pat, fr.eps,
                   fr.roe_percentage, fr.roce_percentage, fr.operating_margin_percentage,
                   fr.net_margin_percentage, fr.cash_flow_from_operations, fr.total_assets,
                   fr.current_assets, fr.current_liabilities, fr.total_debt, fr.total_equity,
                   fr.interest_expense, fr.ebit
            FROM financial.financial_results fr
            JOIN reference.instruments i ON i.id = fr.instrument_id
            WHERE fr.instrument_id = ?
            ORDER BY fr.period_end ASC
            """,
            (rs, rowNum) -> new FinancialResult(
                (UUID) rs.getObject("instrument_id"), rs.getString("symbol"),
                rs.getDate("period_end").toLocalDate(), rs.getString("period_type"),
                rs.getBigDecimal("sales"), rs.getBigDecimal("pat"), rs.getBigDecimal("eps"),
                rs.getBigDecimal("roe_percentage"), rs.getBigDecimal("roce_percentage"),
                rs.getBigDecimal("operating_margin_percentage"), rs.getBigDecimal("net_margin_percentage"),
                rs.getBigDecimal("cash_flow_from_operations"), rs.getBigDecimal("total_assets"),
                rs.getBigDecimal("current_assets"), rs.getBigDecimal("current_liabilities"),
                rs.getBigDecimal("total_debt"), rs.getBigDecimal("total_equity"),
                rs.getBigDecimal("interest_expense"), rs.getBigDecimal("ebit")
            ),
            instrumentId
        );
    }
}
