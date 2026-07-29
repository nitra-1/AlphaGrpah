package com.alphagraph.ownership.api;

import com.alphagraph.common.engine.Score;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The Institutional Engine's output for one instrument, mirroring
 * {@code ownership.institutional_scores} (docs/003_Database_Architecture.md §3a). Same 0-100
 * scale choice as technical.api.TechnicalScore (Module 1.5) and financial.api.FundamentalScore
 * (Module 1.6), matching the roadmap's own worked example ("Institutional Score 93").
 * {@code value()} is {@code institutionalScore}; {@code confidence()} reflects how many of the
 * underlying metrics were actually available - sparse trend/bulk-deal data lowers it, it is not a
 * measure of how strong the accumulation is.
 */
public record InstitutionalScore(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    PromoterStatus promoterStatus, InstitutionalFlowStatus fiiStatus, InstitutionalFlowStatus diiStatus,
    InstitutionalFlowStatus mfStatus, DeliveryStatus deliveryStatus, InstitutionalBehaviour institutionalBehaviour,
    double institutionalScore, double confidence,
    Double promoterPercentage, Double promoterChangePercentage, Double fiiChangePercentage,
    Double diiChangePercentage, Double mfChangePercentage, Double avgDeliveryPercentage,
    Double relativeVolume, Long netBulkDealQuantity,
    int ruleSetVersion, Instant computedAt
) implements Score {

    @Override
    public double value() {
        return institutionalScore;
    }

    @Override
    public double confidence() {
        return confidence;
    }
}
