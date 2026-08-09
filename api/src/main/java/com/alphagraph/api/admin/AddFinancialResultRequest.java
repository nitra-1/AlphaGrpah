package com.alphagraph.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Every field except symbol/periodEnd/periodType/sales/pat is nullable by design - matching
 * financial.financial_results' own schema (only those five are NOT NULL). Leaving a field blank
 * is the correct, expected action when the number can't be found, not a workaround - see
 * docs/006_Universe_Expansion_Runbook.md §4's "leave blank rather than guess" rule.
 */
public record AddFinancialResultRequest(
    @NotBlank String symbol,
    @NotNull LocalDate periodEnd,
    @NotBlank String periodType,
    @NotNull BigDecimal sales,
    @NotNull BigDecimal pat,
    BigDecimal eps,
    BigDecimal roePercentage,
    BigDecimal rocePercentage,
    BigDecimal operatingMarginPercentage,
    BigDecimal netMarginPercentage,
    BigDecimal cashFlowFromOperations,
    BigDecimal totalAssets,
    BigDecimal currentAssets,
    BigDecimal currentLiabilities,
    BigDecimal totalDebt,
    BigDecimal totalEquity,
    BigDecimal interestExpense,
    BigDecimal ebit
) {
}
