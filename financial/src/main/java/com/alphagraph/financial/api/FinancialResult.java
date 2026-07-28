package com.alphagraph.financial.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One period's fundamentals for one instrument, mirroring {@code financial.financial_results}
 * (docs/003_Database_Architecture.md §3a). Only {@code sales} and {@code pat} are guaranteed
 * present - every listed company reports those two every period regardless of sector. Everything
 * else is nullable: banks/NBFCs don't report a conventional "sales" line ({@code sales} holds net
 * interest income for them instead, see the loader javadoc), ROCE isn't a standard disclosure for
 * banks, and EPS/cash-flow-from-operations aren't always broken out at the individual-quarter
 * level in every summary source.
 */
public record FinancialResult(
    UUID instrumentId, String symbol, LocalDate periodEnd, String periodType,
    BigDecimal sales, BigDecimal pat, BigDecimal eps,
    BigDecimal roePercentage, BigDecimal rocePercentage,
    BigDecimal operatingMarginPercentage, BigDecimal netMarginPercentage,
    BigDecimal cashFlowFromOperations
) {
}
