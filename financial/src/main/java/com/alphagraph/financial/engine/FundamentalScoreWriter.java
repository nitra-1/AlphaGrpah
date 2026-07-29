package com.alphagraph.financial.engine;

import com.alphagraph.financial.api.FundamentalScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Upserts on (instrument_id, as_of_date) — idempotent, matching every other module's
 * Loader/ScoreWriter convention (docs/002_Engine_Architecture.md §5).
 */
@Component
public class FundamentalScoreWriter {

    private final JdbcTemplate jdbcTemplate;

    public FundamentalScoreWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void write(FundamentalScore score) {
        jdbcTemplate.update(
            """
            INSERT INTO financial.fundamental_scores
                (id, instrument_id, as_of_date, business_growth, profitability, capital_efficiency,
                 financial_quality, financial_score, confidence, revenue_growth_percentage,
                 pat_growth_percentage, eps_growth_percentage, roe_percentage, roce_percentage,
                 operating_margin_percentage, net_margin_percentage, asset_turnover, working_capital,
                 cash_conversion_ratio, debt_to_equity, interest_coverage, rule_set_version, computed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                business_growth = EXCLUDED.business_growth, profitability = EXCLUDED.profitability,
                capital_efficiency = EXCLUDED.capital_efficiency, financial_quality = EXCLUDED.financial_quality,
                financial_score = EXCLUDED.financial_score, confidence = EXCLUDED.confidence,
                revenue_growth_percentage = EXCLUDED.revenue_growth_percentage,
                pat_growth_percentage = EXCLUDED.pat_growth_percentage,
                eps_growth_percentage = EXCLUDED.eps_growth_percentage, roe_percentage = EXCLUDED.roe_percentage,
                roce_percentage = EXCLUDED.roce_percentage, operating_margin_percentage = EXCLUDED.operating_margin_percentage,
                net_margin_percentage = EXCLUDED.net_margin_percentage, asset_turnover = EXCLUDED.asset_turnover,
                working_capital = EXCLUDED.working_capital, cash_conversion_ratio = EXCLUDED.cash_conversion_ratio,
                debt_to_equity = EXCLUDED.debt_to_equity, interest_coverage = EXCLUDED.interest_coverage,
                rule_set_version = EXCLUDED.rule_set_version, computed_at = EXCLUDED.computed_at
            """,
            UUID.randomUUID(), score.instrumentId(), score.asOfDate(), score.businessGrowth().name(),
            score.profitability().name(), score.capitalEfficiency().name(), score.financialQuality().name(),
            score.financialScore(), score.confidence(), score.revenueGrowthPercentage(), score.patGrowthPercentage(),
            score.epsGrowthPercentage(), score.roePercentage(), score.rocePercentage(), score.operatingMarginPercentage(),
            score.netMarginPercentage(), score.assetTurnover(), score.workingCapital(), score.cashConversionRatio(),
            score.debtToEquity(), score.interestCoverage(), score.ruleSetVersion(), Timestamp.from(score.computedAt())
        );
    }
}
