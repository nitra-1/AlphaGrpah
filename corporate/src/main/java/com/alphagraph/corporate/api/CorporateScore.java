package com.alphagraph.corporate.api;

import com.alphagraph.common.engine.Score;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Module 2.8: the Corporate Signal Engine's output for one instrument at one point in time,
 * mirroring {@code corporate.corporate_scores} - combines Order Book quality, Management growth
 * visibility, News catalyst strength, and Corporate Event signal into one composite. The four
 * contributing domain scores are carried alongside the composite (nullable - an instrument with
 * no history in a given domain simply contributes 0 to that domain, per the same null-tolerant
 * pattern every cross-domain orchestrator in this project already uses) so a future consumer
 * (Module 2.9's AI Analyst) can explain which domain drove a score change, not just cite the
 * final number.
 */
public record CorporateScore(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    double corporateScore, CorporateRating corporateRating,
    Double orderBookScore, Double managementScore, Double newsCatalystScore, int eventNetSignal,
    double confidence, int ruleSetVersion, Instant computedAt
) implements Score {

    @Override
    public double value() {
        return corporateScore;
    }
}
