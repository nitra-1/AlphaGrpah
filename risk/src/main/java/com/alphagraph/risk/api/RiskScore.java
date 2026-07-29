package com.alphagraph.risk.api;

import com.alphagraph.common.engine.Score;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The Risk Engine's output, mirroring {@code risk.risk_scores} (docs/003_Database_Architecture.md
 * §3a). Same 0-100 scale direction as every prior Phase 1 engine (Modules 1.5-1.8) for
 * consistency: {@code value()} is {@code riskScore}, and HIGHER STILL MEANS BETTER/SAFER here,
 * not riskier - a 100 means no risk factors detected and several safety signals present, a 0
 * means the opposite. Event Risk (litigation, governance, pledged shares, auditor resignation) is
 * deliberately absent - descoped for this module, no confirmed free structured data source.
 */
public record RiskScore(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    RiskLevel businessRisk, RiskLevel technicalRisk, RiskLevel ownershipRisk, RiskLevel valuationRisk,
    RiskLevel overallRisk,
    double riskScore, double confidence,
    Double peRatio, Double pbRatio, Double debtToEquity,
    int ruleSetVersion, Instant computedAt
) implements Score {

    @Override
    public double value() {
        return riskScore;
    }

    @Override
    public double confidence() {
        return confidence;
    }
}
