package com.alphagraph.financial.results;

import com.alphagraph.common.etl.Loader;
import com.alphagraph.financial.api.FinancialResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Upserts on (instrument_id, period_end, period_type) — idempotent per
 * docs/002_Engine_Architecture.md §2.
 *
 * Note on {@code sales} for banks/NBFCs (e.g. HDFCBANK, ICICIBANK in the bundled sample): they
 * don't report a conventional "sales" line the way a manufacturer or IT services company does, so
 * the sample CSV substitutes net interest income there. This column will need a sector-aware
 * definition once the Fundamental Engine (a later module) starts comparing companies across
 * sectors - flagged here rather than silently glossed over.
 */
@Component
public class FinancialResultsLoader implements Loader<FinancialResult> {

    private final JdbcTemplate jdbcTemplate;

    public FinancialResultsLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void load(FinancialResult result) {
        jdbcTemplate.update(
            """
            INSERT INTO financial.financial_results
                (id, instrument_id, period_end, period_type, sales, pat, eps, roe_percentage,
                 roce_percentage, operating_margin_percentage, net_margin_percentage, cash_flow_from_operations)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, period_end, period_type) DO UPDATE SET
                sales = EXCLUDED.sales, pat = EXCLUDED.pat, eps = EXCLUDED.eps,
                roe_percentage = EXCLUDED.roe_percentage, roce_percentage = EXCLUDED.roce_percentage,
                operating_margin_percentage = EXCLUDED.operating_margin_percentage,
                net_margin_percentage = EXCLUDED.net_margin_percentage,
                cash_flow_from_operations = EXCLUDED.cash_flow_from_operations
            """,
            UUID.randomUUID(), result.instrumentId(), result.periodEnd(), result.periodType(),
            result.sales(), result.pat(), result.eps(), result.roePercentage(), result.rocePercentage(),
            result.operatingMarginPercentage(), result.netMarginPercentage(), result.cashFlowFromOperations()
        );
    }
}
