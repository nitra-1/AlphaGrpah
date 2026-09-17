package com.alphagraph.financial.transformation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One real quarter's normalized figures for one instrument, straight from NSE's
 * {@code results-comparision} feed - no DB round-trip, the live feed's own 5 returned quarters
 * are the period history the engine walks. {@code revenue}/{@code interestExpense} are null for a
 * bank filer (the real feed simply doesn't carry those fields for banks); {@code operatingMarginPct}
 * is derived (profit before tax + interest - other income, over revenue) so it's null whenever any
 * of those three inputs is null or revenue is zero, never a guessed partial computation.
 */
record FinancialResultsPeriod(
    UUID instrumentId, String symbol, LocalDate periodEnd,
    BigDecimal revenue, BigDecimal pat, BigDecimal operatingMarginPct, BigDecimal interestExpense
) {
    BigDecimal valueFor(FinancialMetric metric) {
        return switch (metric) {
            case REVENUE -> revenue;
            case PAT -> pat;
            case OPERATING_MARGIN -> operatingMarginPct;
            case INTEREST_EXPENSE -> interestExpense;
        };
    }
}
