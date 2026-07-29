package com.alphagraph.financial.api;

import com.alphagraph.common.engine.Score;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The Fundamental Engine's output for one instrument on one period, mirroring
 * {@code financial.fundamental_scores} (docs/003_Database_Architecture.md §3a). Same 0-100 scale
 * choice as {@code technical.api.TechnicalScore} (Module 1.5), for the same reason: it matches
 * the roadmap's own worked example ("Financial Score 91"). {@code value()} is
 * {@code financialScore}; {@code confidence()} reflects how many of the underlying metrics were
 * actually available (see {@code FundamentalEngine} - sparse balance sheet/growth data lowers it,
 * it is not a measure of how good the business is).
 */
public record FundamentalScore(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    BusinessGrowth businessGrowth, Profitability profitability,
    CapitalEfficiency capitalEfficiency, FinancialQuality financialQuality,
    double financialScore, double confidence,
    Double revenueGrowthPercentage, Double patGrowthPercentage, Double epsGrowthPercentage,
    Double roePercentage, Double rocePercentage, Double operatingMarginPercentage, Double netMarginPercentage,
    Double assetTurnover, Double workingCapital, Double cashConversionRatio,
    Double debtToEquity, Double interestCoverage,
    int ruleSetVersion, Instant computedAt
) implements Score {

    @Override
    public double value() {
        return financialScore;
    }

    @Override
    public double confidence() {
        return confidence;
    }
}
