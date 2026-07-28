package com.alphagraph.ownership.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One quarter's shareholding breakdown for one instrument, mirroring
 * {@code ownership.shareholding_pattern} (docs/003_Database_Architecture.md §3a).
 * {@code mfPercentage} is a sub-category of {@code diiPercentage} (not additive) when present;
 * {@code publicPercentage} is nullable since compiled sample data doesn't always account for
 * every minor category cleanly.
 */
public record ShareholdingPattern(
    UUID instrumentId, String symbol, LocalDate periodEnd,
    BigDecimal promoterPercentage, BigDecimal fiiPercentage, BigDecimal diiPercentage,
    BigDecimal mfPercentage, BigDecimal publicPercentage
) {
}
