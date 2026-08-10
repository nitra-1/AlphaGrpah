package com.alphagraph.api.financial;

import com.alphagraph.financial.api.FinancialResult;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One reported period, read-only - instrumentId/symbol are already known to the caller (path param), so aren't repeated here. */
public record FinancialHistoryEntryDto(
    LocalDate periodEnd, String periodType, BigDecimal sales, BigDecimal pat, BigDecimal eps,
    BigDecimal roePercentage, BigDecimal rocePercentage, BigDecimal operatingMarginPercentage, BigDecimal netMarginPercentage,
    BigDecimal cashFlowFromOperations, BigDecimal totalAssets, BigDecimal currentAssets, BigDecimal currentLiabilities,
    BigDecimal totalDebt, BigDecimal totalEquity, BigDecimal interestExpense, BigDecimal ebit
) {
    public static FinancialHistoryEntryDto from(FinancialResult r) {
        return new FinancialHistoryEntryDto(
            r.periodEnd(), r.periodType(), r.sales(), r.pat(), r.eps(),
            r.roePercentage(), r.rocePercentage(), r.operatingMarginPercentage(), r.netMarginPercentage(),
            r.cashFlowFromOperations(), r.totalAssets(), r.currentAssets(), r.currentLiabilities(),
            r.totalDebt(), r.totalEquity(), r.interestExpense(), r.ebit()
        );
    }
}
