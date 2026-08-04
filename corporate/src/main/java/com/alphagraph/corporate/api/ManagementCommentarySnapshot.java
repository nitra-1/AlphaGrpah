package com.alphagraph.corporate.api;

import com.alphagraph.common.engine.Score;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Layer 2: the Management Commentary Engine's output for one instrument at one point in time,
 * mirroring {@code corporate.management_commentary_snapshots}. Implements {@link Score} - like
 * {@code OrderBookSnapshot} and unlike {@code CorporateEvent}, aggregating observations into a
 * numeric score via {@code common.rules.RuleSet} threshold rules is exactly the shape every Phase
 * 1 engine already has.
 */
public record ManagementCommentarySnapshot(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    double growthVisibilityScore, GuidanceTrend guidanceTrend, ManagementCredibility managementCredibility,
    double confidence, int ruleSetVersion, Instant computedAt
) implements Score {

    @Override
    public double value() {
        return growthVisibilityScore;
    }
}
