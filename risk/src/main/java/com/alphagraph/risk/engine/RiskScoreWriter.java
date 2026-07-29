package com.alphagraph.risk.engine;

import com.alphagraph.risk.api.RiskScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Upserts on (instrument_id, as_of_date) — idempotent, matching every other module's
 * Loader/ScoreWriter convention (docs/002_Engine_Architecture.md §5).
 */
@Component
public class RiskScoreWriter {

    private final JdbcTemplate jdbcTemplate;

    public RiskScoreWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void write(RiskScore score) {
        jdbcTemplate.update(
            """
            INSERT INTO risk.risk_scores
                (id, instrument_id, as_of_date, business_risk, technical_risk, ownership_risk,
                 valuation_risk, overall_risk, risk_score, confidence, pe_ratio, pb_ratio,
                 debt_to_equity, rule_set_version, computed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                business_risk = EXCLUDED.business_risk, technical_risk = EXCLUDED.technical_risk,
                ownership_risk = EXCLUDED.ownership_risk, valuation_risk = EXCLUDED.valuation_risk,
                overall_risk = EXCLUDED.overall_risk, risk_score = EXCLUDED.risk_score,
                confidence = EXCLUDED.confidence, pe_ratio = EXCLUDED.pe_ratio, pb_ratio = EXCLUDED.pb_ratio,
                debt_to_equity = EXCLUDED.debt_to_equity, rule_set_version = EXCLUDED.rule_set_version,
                computed_at = EXCLUDED.computed_at
            """,
            UUID.randomUUID(), score.instrumentId(), score.asOfDate(), score.businessRisk().name(),
            score.technicalRisk().name(), score.ownershipRisk().name(), score.valuationRisk().name(),
            score.overallRisk().name(), score.riskScore(), score.confidence(), score.peRatio(),
            score.pbRatio(), score.debtToEquity(), score.ruleSetVersion(), Timestamp.from(score.computedAt())
        );
    }
}
