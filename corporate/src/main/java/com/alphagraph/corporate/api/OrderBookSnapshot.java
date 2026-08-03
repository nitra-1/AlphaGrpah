package com.alphagraph.corporate.api;

import com.alphagraph.common.engine.Score;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The Order Book Engine's output for one instrument at one point in time, mirroring
 * {@code corporate.order_book_snapshots} (docs/003_Database_Architecture.md §3a). Unlike
 * {@link CorporateEvent} (Module 2.3's output, which deliberately does not implement
 * {@code Score}), this DOES implement {@link Score} - aggregating a running order-book ledger into
 * a numeric quality score via {@code common.rules.RuleSet} threshold rules is exactly the shape
 * every Phase 1 engine already has, unlike Module 2.3's topic-matching classification.
 *
 * <p>{@code orderBookGrowthPct} is null on an instrument's first-ever snapshot - there is no prior
 * value to compare against, and this is left genuinely absent rather than fabricated as 0%.
 */
public record OrderBookSnapshot(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    double currentOrderBookCrore, Double orderBookGrowthPct, double executionVisibilityYears, int orderCount,
    OrderQuality orderQuality, double qualityScore, double confidence,
    int ruleSetVersion, Instant computedAt
) implements Score {

    @Override
    public double value() {
        return qualityScore;
    }
}
